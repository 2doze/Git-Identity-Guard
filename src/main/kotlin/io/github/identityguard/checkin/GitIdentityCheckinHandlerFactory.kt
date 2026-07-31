package io.github.identityguard.checkin

import io.github.identityguard.GitIdentityGuardBundle
import io.github.identityguard.git.ConfigWriteResult
import io.github.identityguard.git.GitConfigGateway
import io.github.identityguard.service.CheckSnapshot
import io.github.identityguard.service.GitIdentityGuardSettings
import io.github.identityguard.service.RemoteSnapshotService
import io.github.identityguard.util.IdentityUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.intellij.util.PairConsumer
import git4idea.repo.GitRepositoryManager

/**
 * Commit 前校验处理器工厂（F2 第 3 项、F3 方案 B），经 plugin.xml checkinHandlerFactory 注册。
 * 是否生效由设置 preCommitCheckEnabled 控制。
 */
class GitIdentityCheckinHandlerFactory : CheckinHandlerFactory() {

    override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler =
        GitIdentityCheckinHandler(panel.project, panel)
}

/**
 * Commit 前校验处理器。
 *
 * 关键约束（§3.4 / F3.1.5）：
 * - `beforeCheckin` 在 EDT 上执行，**不允许**在这里跑外部 git——
 *   只读监听器预计算好的 [CheckSnapshot]（F2.4）；
 * - "立即切换并继续 Commit"：写入在模态进度（后台线程）中完成，
 *   **全部成功才允许提交继续**；任一失败则取消提交并展示原因（F3.1.2，不得伪装成功）；
 * - "本次继续使用当前邮箱"：不保存任何抑制状态，下次提交仍会询问（F3.1.3）。
 */
class GitIdentityCheckinHandler(
    private val project: Project,
    private val panel: CheckinProjectPanel
) : CheckinHandler() {

    override fun beforeCheckin(
        executor: CommitExecutor?,
        additionalDataConsumer: PairConsumer<in Any?, in Any?>?
    ): ReturnResult {
        if (!GitIdentityGuardSettings.getInstance().preCommitCheckEnabled) {
            return ReturnResult.COMMIT
        }

        // 只看与本次提交文件相关的仓库快照（不重新跑 git）
        val affected = affectedMismatchSnapshots()
        if (affected.isEmpty()) return ReturnResult.COMMIT

        val snap = affected.first()
        val login = snap.matchedAccountLogin ?: ""
        // 弹窗展示完整隐私身份对照：当前 vs 推荐（邮箱 + 用户名）
        val message = GitIdentityGuardBundle.message(
            "checkin.message",
            login,
            displayField(snap.effectiveEmail),
            displayField(snap.effectiveName),
            snap.privacyEmail ?: "",
            login
        )
        val options = arrayOf(
            GitIdentityGuardBundle.message("checkin.apply"),
            GitIdentityGuardBundle.message("checkin.continue"),
            GitIdentityGuardBundle.message("checkin.cancel")
        )
        // 0 = 切换并继续，1 = 本次继续，其余 = 取消
        val choice = Messages.showDialog(
            project,
            message,
            GitIdentityGuardBundle.message("checkin.title"),
            options,
            0,
            Messages.getWarningIcon()
        )
        return when (choice) {
            0 -> if (switchAll(affected)) ReturnResult.COMMIT else ReturnResult.CANCEL
            1 -> ReturnResult.COMMIT
            else -> ReturnResult.CANCEL
        }
    }

    /** 过滤出"本次提交文件所在仓库"中处于不匹配状态的快照 */
    private fun affectedMismatchSnapshots(): List<CheckSnapshot> {
        val roots = project.service<RemoteSnapshotService>().mismatchedSnapshots()
        if (roots.isEmpty()) return emptyList()
        val files = panel.virtualFiles
        return roots.filter { snap ->
            val rootPrefix = snap.repoRootPath.trimEnd('/') + "/"
            files.any { it.path == snap.repoRootPath || it.path.startsWith(rootPrefix) }
        }
    }

    /**
     * 模态进度下批量写入：body 在后台线程执行，EDT 仅阻塞于进度对话框。
     * 全部仓库写入成功才返回 true（F3.1.5 的时序约束）。
     */
    private fun switchAll(snapshots: List<CheckSnapshot>): Boolean {
        var allOk = true
        val errors = StringBuilder()
        ProgressManager.getInstance().run(object : Task.Modal(
            project, GitIdentityGuardBundle.message("task.applying"), false
        ) {
            override fun run(indicator: ProgressIndicator) {
                if (project.isDisposed) {
                    allOk = false
                    return
                }
                val reposByPath = GitRepositoryManager.getInstance(project).repositories.associateBy { it.root.path }
                val settings = GitIdentityGuardSettings.getInstance()
                for (snap in snapshots) {
                    val repo = reposByPath[snap.repoRootPath]
                    val login = snap.matchedAccountLogin
                    val email = snap.privacyEmail
                    if (repo == null || email == null || login == null) {
                        allOk = false
                        errors.appendLine("${snap.repoPresentableUrl}: 仓库或隐私身份不可用")
                        continue
                    }
                    // 与校验器一致的判定：只写"需要修正"的字段（§3.3.4 拆分写入）
                    val lenientEmail = settings.isLenientPass(snap.verifiedEmails, snap.effectiveEmail)
                    val needEmail = !IdentityUtil.emailsMatch(snap.effectiveEmail, email) && !lenientEmail
                    val needName = !IdentityUtil.namesMatch(snap.effectiveName, login)
                    if (needEmail) {
                        when (val r = GitConfigGateway.writeLocalUserEmail(project, repo, email)) {
                            is ConfigWriteResult.Failure -> {
                                allOk = false
                                errors.appendLine("${snap.repoPresentableUrl}: user.email ${r.reason}")
                            }
                            is ConfigWriteResult.Success -> Unit
                        }
                    }
                    if (needName) {
                        when (val r = GitConfigGateway.writeLocalUserName(project, repo, login)) {
                            is ConfigWriteResult.Failure -> {
                                allOk = false
                                errors.appendLine("${snap.repoPresentableUrl}: user.name ${r.reason}")
                            }
                            is ConfigWriteResult.Success -> Unit
                        }
                    }
                }
            }
        })
        if (!allOk) {
            // 失败后让用户重新发起提交、重新选择（F3.1.2）
            Messages.showErrorDialog(
                project,
                errors.toString(),
                GitIdentityGuardBundle.message("checkin.switch.failure")
            )
        }
        return allOk
    }

    private fun displayField(value: String?): String =
        value?.trim()?.takeIf { it.isNotEmpty() }
            ?: GitIdentityGuardBundle.message("email.unset")
}
