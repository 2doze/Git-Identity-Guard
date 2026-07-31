package io.github.identityguard.notify

import io.github.identityguard.GitIdentityGuardBundle
import io.github.identityguard.GitIdentityGuardPlugin
import io.github.identityguard.git.ConfigWriteResult
import io.github.identityguard.git.GitConfigGateway
import io.github.identityguard.service.GitIdentityGuardSettings
import io.github.identityguard.service.IgnoreListService
import io.github.identityguard.util.IgnoreKey
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.notification.NotificationAction
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

/**
 * 通知构造与展示（设计文档 F3 方案 A）。
 *
 * 线程约束（§3.4）：
 * - 展示通知本身线程安全（Notifications.Bus.notify）；
 * - "应用隐私邮箱"动作回调在 EDT 上，写入必须转移到后台任务。
 */
object GitIdentityNotifier {

    /** 身份不匹配主通知：WARNING 级别，带 [应用隐私身份] / [忽略该仓库] 两个动作 */
    fun notifyMismatch(
        project: Project,
        repository: GitRepository,
        accountLogin: String,
        effectiveEmail: String?,
        effectiveName: String?,
        privacyEmail: String,
        needEmail: Boolean,
        needName: Boolean,
        ignoreKey: IgnoreKey
    ) {
        val settings = GitIdentityGuardSettings.getInstance()
        val content = if (settings.showCurrentEmailInNotification) {
            GitIdentityGuardBundle.message(
                "notification.content.full",
                accountLogin,
                displayField(effectiveEmail),
                displayField(effectiveName),
                privacyEmail
            )
        } else {
            GitIdentityGuardBundle.message("notification.content.short", accountLogin)
        }

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(GitIdentityGuardPlugin.NOTIFICATION_GROUP_ID)
            .createNotification(
                GitIdentityGuardBundle.message("notification.title"),
                content,
                NotificationType.WARNING
            )

        val rootPath = repository.root.path
        // 点击动作的回调在 EDT 上，内部转后台执行 git 写入
        notification.addAction(
            NotificationAction.createSimpleExpiring(GitIdentityGuardBundle.message("action.apply")) {
                applyInBackground(project, rootPath, privacyEmail, accountLogin, needEmail, needName)
            }
        )
        notification.addAction(
            NotificationAction.createSimpleExpiring(GitIdentityGuardBundle.message("action.ignore")) {
                project.service<IgnoreListService>().ignore(ignoreKey)
            }
        )

        Notifications.Bus.notify(notification, project)
    }

    /**
     * 后台应用隐私身份：只写"需要修正"的字段（§3.3.4 拆分独立写入）。
     * 这样不会覆盖用户经宽松放行刻意保留的邮箱，也不会重复写已经正确的字段。
     * 仓库对象按根路径在点击时重新查找（通知可能晚于仓库状态变化）。
     */
    private fun applyInBackground(
        project: Project,
        repoRootPath: String,
        privacyEmail: String,
        accountLogin: String,
        needEmail: Boolean,
        needName: Boolean
    ) {
        if (project.isDisposed) return
        object : Task.Backgroundable(project, GitIdentityGuardBundle.message("task.applying"), false) {
            override fun run(indicator: ProgressIndicator) {
                if (project.isDisposed) return
                val repo = GitRepositoryManager.getInstance(project).repositories
                    .firstOrNull { it.root.path == repoRootPath }
                if (repo == null) {
                    notifyFailure(project, repoRootPath, "仓库当前不可用")
                    return
                }
                var wroteEmail = false
                var wroteName = false
                val failures = mutableListOf<String>()
                // 邮箱：仅在需要时写
                if (needEmail) {
                    when (val r = GitConfigGateway.writeLocalUserEmail(project, repo, privacyEmail)) {
                        is ConfigWriteResult.Success -> wroteEmail = true
                        is ConfigWriteResult.Failure -> failures += "user.email: ${r.reason}"
                    }
                }
                // 用户名：仅在需要时写（隐私身份的组成部分）
                if (needName) {
                    when (val r = GitConfigGateway.writeLocalUserName(project, repo, accountLogin)) {
                        is ConfigWriteResult.Success -> wroteName = true
                        is ConfigWriteResult.Failure -> failures += "user.name: ${r.reason}"
                    }
                }
                if (failures.isNotEmpty()) {
                    notifyFailure(project, repo.presentableUrl, failures.joinToString("；"))
                } else {
                    notifySuccess(project, repo.presentableUrl, wroteEmail, wroteName, privacyEmail, accountLogin)
                }
            }
        }.queue()
    }

    private fun notify(project: Project, title: String, content: String, type: NotificationType) {
        if (project.isDisposed) return
        Notifications.Bus.notify(
            NotificationGroupManager.getInstance()
                .getNotificationGroup(GitIdentityGuardPlugin.NOTIFICATION_GROUP_ID)
                .createNotification(title, content, type),
            project
        )
    }

    /** 成功：按实际写入的字段选择文案 */
    private fun notifySuccess(
        project: Project,
        repoPresentable: String,
        wroteEmail: Boolean,
        wroteName: Boolean,
        privacyEmail: String,
        accountLogin: String
    ) {
        val key = when {
            wroteEmail && wroteName -> "notify.apply.success.both.content"
            wroteName -> "notify.apply.success.name.content"
            else -> "notify.apply.success.content"
        }
        val content = when {
            wroteEmail && wroteName ->
                GitIdentityGuardBundle.message(key, repoPresentable, accountLogin, privacyEmail)
            wroteName -> GitIdentityGuardBundle.message(key, repoPresentable, accountLogin)
            else -> GitIdentityGuardBundle.message(key, repoPresentable, privacyEmail)
        }
        notify(project, GitIdentityGuardBundle.message("notify.apply.success"), content, NotificationType.INFORMATION)
    }

    /** 失败：提示原因（F3.1.2） */
    private fun notifyFailure(project: Project, repoPresentable: String, reason: String) {
        notify(
            project,
            GitIdentityGuardBundle.message("notify.apply.failure"),
            GitIdentityGuardBundle.message("notify.apply.failure.content", repoPresentable, reason),
            NotificationType.ERROR
        )
    }

    /** 展示用字段：未设置时显示占位文案（F2.3） */
    private fun displayField(value: String?): String =
        value?.trim()?.takeIf { it.isNotEmpty() }
            ?: GitIdentityGuardBundle.message("email.unset")
}
