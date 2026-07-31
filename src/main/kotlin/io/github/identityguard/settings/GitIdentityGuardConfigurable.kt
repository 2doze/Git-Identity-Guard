package io.github.identityguard.settings

import io.github.identityguard.GitIdentityGuardBundle
import io.github.identityguard.github.PrivacyEmailFetchResult
import io.github.identityguard.github.PrivacyEmailFetcher
import io.github.identityguard.service.GitIdentityGuardSettings
import io.github.identityguard.service.IgnoreListService
import io.github.identityguard.service.PrivacyEmailCacheService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import com.intellij.ui.table.JBTable
import org.jetbrains.plugins.github.authentication.GHAccountsUtil
import javax.swing.JComponent
import javax.swing.JTextArea
import javax.swing.table.DefaultTableModel

/**
 * 插件设置页（设计文档 §4），注册在 Settings -> Tools 下。
 *
 * 注册为 project 级 configurable 而非 application 级的原因：
 * "手动刷新"需要经 GithubApiRequestExecutorManager.getExecutor(account, project)
 * 借用账号凭据，该 API 要求 Project 参数（§4 账号列表管理）。
 * 设置数据本身仍是 application 级。
 */
class GitIdentityGuardConfigurable(private val project: Project) : SearchableConfigurable {

    private val settings: GitIdentityGuardSettings get() = GitIdentityGuardSettings.getInstance()

    private var rootPanel: DialogPanel? = null
    private var allowlistArea: JTextArea? = null

    private val accountsModel = object : DefaultTableModel(ACCOUNT_COLUMNS, 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    private val ignoreModel = object : DefaultTableModel(IGNORE_COLUMNS, 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }

    override fun getId(): String = "io.github.identityguard.settings"

    override fun getDisplayName(): String = GitIdentityGuardBundle.message("settings.title")

    override fun createComponent(): JComponent {
        val accountsTable = JBTable(accountsModel)
        val ignoreTable = JBTable(ignoreModel)

        val panel = panel {
            // 通知组可能被用户全局静默，给出检查入口提示（§4 通知状态提示）
            row { label(GitIdentityGuardBundle.message("settings.notification.hint")) }
            separator()
            row {
                checkBox(GitIdentityGuardBundle.message("settings.precommit.label"))
                    .bindSelected(settings::preCommitCheckEnabled)
            }
            row {
                checkBox(GitIdentityGuardBundle.message("settings.showemail.label"))
                    .bindSelected(settings::showCurrentEmailInNotification)
            }
            row {
                checkBox(GitIdentityGuardBundle.message("settings.autoenable.label"))
                    .bindSelected(settings::autoEnablePreCommitOnMismatch)
            }
            row {
                checkBox(GitIdentityGuardBundle.message("settings.lenient.label"))
                    .bindSelected(settings::lenientPassMode)
            }
            row(GitIdentityGuardBundle.message("settings.allowlist.label")) {
                // 白名单文本框：不走 bind，在 apply/reset 中手工同步（换行分隔多值）
                val cell = textArea().rows(4).columns(50)
                allowlistArea = cell.component
            }
            group(GitIdentityGuardBundle.message("settings.accounts.title")) {
                row { cell(JBScrollPane(accountsTable)).align(Align.FILL) }
                row {
                    button(GitIdentityGuardBundle.message("settings.accounts.refresh")) {
                        refreshPrivacyEmails()
                    }
                }
            }
            group(GitIdentityGuardBundle.message("settings.ignore.title")) {
                row { cell(JBScrollPane(ignoreTable)).align(Align.FILL) }
                row {
                    button(GitIdentityGuardBundle.message("settings.ignore.remove")) {
                        removeSelectedIgnores(ignoreTable)
                    }
                }
            }
        }

        rootPanel = panel
        refreshAccountsTable()
        refreshIgnoreTable()
        syncAllowlistFromSettings()
        return panel
    }

    override fun isModified(): Boolean {
        val panelModified = rootPanel?.isModified() ?: false
        return panelModified || parseAllowlist() != settings.allowlistEmails
    }

    override fun apply() {
        rootPanel?.apply()
        settings.allowlistEmails = parseAllowlist()
    }

    override fun reset() {
        rootPanel?.reset()
        syncAllowlistFromSettings()
        refreshAccountsTable()
        refreshIgnoreTable()
    }

    override fun disposeUIResources() {
        rootPanel = null
        allowlistArea = null
    }

    // ---------- 白名单 ----------

    /** 解析文本框：按行切分，规范化交由 settings 的 setter 完成 */
    private fun parseAllowlist(): List<String> =
        allowlistArea?.text?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    private fun syncAllowlistFromSettings() {
        allowlistArea?.text = settings.allowlistEmails.joinToString("\n")
    }

    // ---------- 账号缓存表 ----------

    /** 后台刷新全部 github.com 账号的隐私邮箱（F1.2 失败处理：只记状态不打扰） */
    private fun refreshPrivacyEmails() {
        object : Task.Backgroundable(project, GitIdentityGuardBundle.message("settings.accounts.refreshing"), false) {
            override fun run(indicator: ProgressIndicator) {
                // GHAccountsUtil.accounts 是公开门面（GithubAuthenticationManager.accounts 为 internal）
                val accounts = GHAccountsUtil.accounts
                val cache = PrivacyEmailCacheService.getInstance()
                for (account in accounts) {
                    if (!account.server.isGithubDotCom) continue
                    when (val result = PrivacyEmailFetcher.fetch(account, project)) {
                        is PrivacyEmailFetchResult.Success -> cache.putSuccess(account, result)
                        is PrivacyEmailFetchResult.Failure -> cache.putFailure(account, result)
                    }
                }
                // 顺带清理已登出账号的遗留缓存（F1.3）
                cache.pruneTo(accounts.map { it.id }.toSet())
                ApplicationManager.getApplication().invokeLater({
                    refreshAccountsTable()
                    // 刷新后强制全量重校验：覆盖"账号集合变化后重校验"（F2 第 4 项）——
                    // 2025.3 无账号变化事件，设置页刷新是该场景的显式触发入口
                    io.github.identityguard.check.GitIdentityChecker(project).checkAllRepositories(force = true)
                }, project.disposed)
            }
        }.queue()
    }

    private fun refreshAccountsTable() {
        accountsModel.rowCount = 0
        val cache = PrivacyEmailCacheService.getInstance()
        for (account in GHAccountsUtil.accounts) {
            val isGhe = !account.server.isGithubDotCom
            val entry = cache.allEntries()[account.id]
            val email = if (isGhe) "" else entry?.privacyEmail.orEmpty()
            val status = when {
                isGhe -> GitIdentityGuardBundle.message("settings.accounts.status.ghe")
                entry == null || entry.privacyEmail.isEmpty() ->
                    entry?.lastErrorKey?.let { GitIdentityGuardBundle.message(it) }
                        ?: GitIdentityGuardBundle.message("settings.accounts.status.empty")
                entry.lastErrorKey != null -> GitIdentityGuardBundle.message(entry.lastErrorKey!!)
                entry.usedFallback -> GitIdentityGuardBundle.message("settings.accounts.status.fallback")
                else -> GitIdentityGuardBundle.message("settings.accounts.status.ok")
            }
            accountsModel.addRow(arrayOf(account.name, account.server.host, email, status))
        }
    }

    // ---------- 忽略列表表 ----------

    private fun refreshIgnoreTable() {
        ignoreModel.rowCount = 0
        for (entry in project.service<IgnoreListService>().entries()) {
            ignoreModel.addRow(arrayOf(entry.repoRootPath, entry.accountLogin))
        }
    }

    /** 移除所选忽略项：直接作用于服务（无需等"应用"按钮，设置页即时生效） */
    private fun removeSelectedIgnores(table: JBTable) {
        val service = project.service<IgnoreListService>()
        val entries = service.entries()
        // 倒序移除，避免行号偏移
        for (viewRow in table.selectedRows.sortedDescending()) {
            val modelRow = table.convertRowIndexToModel(viewRow)
            if (modelRow in entries.indices) {
                service.remove(entries[modelRow].toKey())
            }
        }
        refreshIgnoreTable()
    }

    companion object {
        private val ACCOUNT_COLUMNS = arrayOf<Any>(
            GitIdentityGuardBundle.message("settings.accounts.column.account"),
            GitIdentityGuardBundle.message("settings.accounts.column.server"),
            GitIdentityGuardBundle.message("settings.accounts.column.email"),
            GitIdentityGuardBundle.message("settings.accounts.column.status")
        )
        private val IGNORE_COLUMNS = arrayOf<Any>(
            GitIdentityGuardBundle.message("settings.ignore.column.repo"),
            GitIdentityGuardBundle.message("settings.ignore.column.account")
        )
    }
}
