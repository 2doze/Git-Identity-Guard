package io.github.identityguard.check

import io.github.identityguard.git.ConfigReadResult
import io.github.identityguard.git.GitConfigGateway
import io.github.identityguard.github.PrivacyEmailFetchResult
import io.github.identityguard.github.PrivacyEmailFetcher
import io.github.identityguard.notify.GitIdentityNotifier
import io.github.identityguard.service.CheckSnapshot
import io.github.identityguard.service.GitIdentityGuardSettings
import io.github.identityguard.service.IgnoreListService
import io.github.identityguard.service.PrivacyEmailCacheService
import io.github.identityguard.service.PromptStateService
import io.github.identityguard.service.RemoteSnapshotService
import io.github.identityguard.util.GithubRemoteMatcher
import io.github.identityguard.util.IdentityUtil
import io.github.identityguard.util.IgnoreKey
import io.github.identityguard.util.PromptKey
import io.github.identityguard.util.RemoteRef
import io.github.identityguard.util.RepoKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import org.jetbrains.plugins.github.authentication.GHAccountsUtil

/**
 * 校验编排器：串联 F2.3 的完整判定流程。
 *
 * 流程：快照节流 → 账号过滤 → Remote 匹配 → 隐私邮箱(缓存/获取) →
 * 读生效邮箱 → 不匹配判定(含宽松放行) → 忽略列表 → 去重 → 通知。
 *
 * 线程约束（§3.4）：内部包含外部 git 调用与可能的网络请求，
 * 不允许在 EDT 上同步执行；[checkRepository] 会自动把 EDT 调用转移到线程池。
 */
class GitIdentityChecker(private val project: Project) {

    /**
     * 校验单个仓库。
     * @param force true = 忽略快照节流强制完整重校验（账号集合变化等场景，F2 第 4 项）
     */
    fun checkRepository(repository: GitRepository, force: Boolean = false) {
        if (ApplicationManager.getApplication().isDispatchThread) {
            ApplicationManager.getApplication().executeOnPooledThread { doCheck(repository, force) }
        } else {
            doCheck(repository, force)
        }
    }

    /** 校验项目内全部 git 仓库 */
    fun checkAllRepositories(force: Boolean = false) {
        if (project.isDisposed) return
        val repos = GitRepositoryManager.getInstance(project).repositories
        for (repo in repos) {
            checkRepository(repo, force)
        }
    }

    private fun doCheck(repository: GitRepository, force: Boolean) {
        if (project.isDisposed) return
        try {
            runCheck(repository, force)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            // 预期外失败只记日志，不打扰用户（§6.3 错误分级）
            LOG.warn("Git Identity Guard 校验失败: ${repository.root.path}", e)
        }
    }

    private fun runCheck(repository: GitRepository, force: Boolean) {
        val rootPath = repository.root.path
        val urls = collectRemoteUrls(repository)
        val snapshotService = project.service<RemoteSnapshotService>()

        // ---- F2.4 节流：Remote 集合未变化且非强制 → 跳过完整校验 ----
        val changed = snapshotService.updateIfChanged(rootPath, urls)
        if (!force && !changed) return

        LOG.info("[GitIdentityGuard] 开始校验 $rootPath (force=$force)")

        val fingerprint = RepoKeys.remoteFingerprint(urls)
        // 未命中保护范围时的基线快照（供 Commit 前校验读取，避免其再跑 git）
        val notProtected = CheckSnapshot(
            repoRootPath = rootPath,
            repoPresentableUrl = repository.presentableUrl,
            remoteFingerprint = fingerprint,
            protected = false,
            matchedAccountLogin = null,
            privacyEmail = null,
            verifiedEmails = emptyList(),
            effectiveEmail = null,
            effectiveName = null,
            mismatch = false
        )

        // ---- 1. 已登录账号（F5：仅 github.com） ----
        // 注意：GithubAuthenticationManager.accounts 是 internal，外部插件需经公开门面获取
        val accounts = GHAccountsUtil.accounts.filter { it.server.isGithubDotCom }
        LOG.info("[GitIdentityGuard] 当前 github.com 账号: ${accounts.map { it.name }}")

        // 延迟 prune：2025.3 无账号集合变化事件（见 GitIdentityStartupActivity 说明），
        // 改为每次校验时按当前账号集清理过期缓存，登出账号的陈旧条目立即失效（F1.3）
        PrivacyEmailCacheService.getInstance().pruneTo(accounts.map { it.id }.toSet())

        if (accounts.isEmpty()) {
            LOG.info("[GitIdentityGuard] 无已登录 GitHub 账号，跳过")
            snapshotService.putSnapshot(notProtected)
            return
        }

        // ---- 2. Remote 匹配（origin 优先、确定性顺序，F2.3 步骤 3） ----
        val remoteRefs = repository.remotes.map { RemoteRef(it.name, it.urls + it.pushUrls) }
        val matched = GithubRemoteMatcher.findMatchedAccounts(remoteRefs, accounts.map { it.name })
        if (matched.isEmpty()) {
            LOG.info("[GitIdentityGuard] Remote 未命中任何已登录账号，remotes=${remoteRefs.flatMap { it.urls }}")
            snapshotService.putSnapshot(notProtected)
            return
        }
        val (login, _) = matched.entries.first()
        val account = accounts.first { it.name.equals(login, ignoreCase = true) }
        LOG.info("[GitIdentityGuard] 命中账号: $login")

        // ---- 3. 隐私邮箱：缓存优先，未命中或过期则现取（F1.3 TTL） ----
        val cache = PrivacyEmailCacheService.getInstance()
        var entry = cache.get(account)
        if (entry == null || cache.shouldRefresh(account)) {
            when (val result = PrivacyEmailFetcher.fetch(account, project)) {
                is PrivacyEmailFetchResult.Success -> {
                    LOG.info("[GitIdentityGuard] 隐私邮箱获取成功: ${IdentityUtil.maskEmail(result.privacyEmail)} (fallback=${result.usedFallback})")
                    cache.putSuccess(account, result)
                    entry = cache.get(account)
                }
                is PrivacyEmailFetchResult.Failure -> {
                    // 获取失败：记录原因（设置页可见）；有过期旧值则回退继续校验（离线降级），
                    // 无旧值才跳过本轮（F1.2.4 / F1.3）
                    LOG.warn("[GitIdentityGuard] 隐私邮箱获取失败: ${result.reasonKey} / ${result.detail}")
                    cache.putFailure(account, result)
                    if (entry == null) {
                        snapshotService.putSnapshot(notProtected.copy(protected = true, matchedAccountLogin = login))
                        return
                    }
                    LOG.info("[GitIdentityGuard] 隐私邮箱刷新失败，回退过期缓存校验 $login")
                }
            }
        }
        // 防御：理论上不会为空（成功路径已写缓存；失败回退路径 entry 非空）
        val cacheEntry = entry ?: return

        // ---- 4. 读最终生效邮箱（经 git 自身，覆盖 includeIf，F2.3） ----
        val effectiveEmail = when (val read = GitConfigGateway.readEffectiveUserEmail(project, repository)) {
            is ConfigReadResult.Value -> read.value
            is ConfigReadResult.ExecError -> {
                // git 执行失败：跳过本轮，不触发任何提示（§6.3）
                LOG.warn("[GitIdentityGuard] 读取生效邮箱失败: ${read.message}")
                snapshotService.putSnapshot(notProtected.copy(protected = true, matchedAccountLogin = login))
                return
            }
        }

        // 顺带读生效 user.name：供 Commit 前弹窗展示完整身份（v2.3.1）；
        // 读取失败不阻断校验，弹窗按"未设置"展示
        val effectiveName = when (val read = GitConfigGateway.readEffective(project, repository, "user.name")) {
            is ConfigReadResult.Value -> read.value
            is ConfigReadResult.ExecError -> null
        }

        // ---- 5. 不匹配判定：隐私身份 = (noreply 邮箱 + GitHub 用户名) 成对判定 ----
        //    邮箱走宽松放行（F3.2），用户名无宽松概念；任一不符即视为不匹配（v2.3.1 最终修订）
        val settings = GitIdentityGuardSettings.getInstance()
        val lenientEmail = settings.isLenientPass(cacheEntry.verifiedEmails, effectiveEmail)
        val needEmail = !IdentityUtil.emailsMatch(effectiveEmail, cacheEntry.privacyEmail) && !lenientEmail
        val needName = !IdentityUtil.namesMatch(effectiveName, login)
        val mismatch = needEmail || needName
        // 邮箱值脱敏后进日志：生效邮箱可能是用户真实私人邮箱，明文写入 idea.log 属隐私泄漏
        LOG.info(
            "[GitIdentityGuard] 生效邮箱=${IdentityUtil.maskEmail(effectiveEmail) ?: "(未设置)"}" +
                "/期望=${IdentityUtil.maskEmail(cacheEntry.privacyEmail)}(需改=$needEmail); " +
                "生效用户名=${effectiveName ?: "(未设置)"}/期望=$login(需改=$needName); 不匹配=$mismatch"
        )

        snapshotService.putSnapshot(
            CheckSnapshot(
                repoRootPath = rootPath,
                repoPresentableUrl = repository.presentableUrl,
                remoteFingerprint = fingerprint,
                protected = true,
                matchedAccountLogin = login,
                privacyEmail = cacheEntry.privacyEmail,
                verifiedEmails = cacheEntry.verifiedEmails,
                effectiveEmail = effectiveEmail,
                effectiveName = effectiveName,
                mismatch = mismatch
            )
        )
        if (!mismatch) return

        // ---- 6. 高级选项：检测到不匹配时自动开启 Commit 前校验（§4） ----
        if (settings.autoEnablePreCommitOnMismatch && !settings.preCommitCheckEnabled) {
            settings.preCommitCheckEnabled = true
        }

        // ---- 7. 忽略列表（二元组，与邮箱无关，F4.1） ----
        val ignoreKey = IgnoreKey(rootPath, fingerprint, login)
        if (project.service<IgnoreListService>().isIgnored(ignoreKey)) {
            LOG.info("[GitIdentityGuard] 仓库已被用户忽略，不提示")
            return
        }

        // ---- 8. 去重（四元组，状态未变只提示一次，F4.1） ----
        val promptKey = PromptKey(
            rootPath,
            fingerprint,
            login,
            IdentityUtil.canonicalEmail(effectiveEmail),
            IdentityUtil.canonicalEmail(cacheEntry.privacyEmail)
        )
        val promptState = project.service<PromptStateService>()
        if (promptState.shouldSuppress(promptKey)) {
            LOG.info("[GitIdentityGuard] 去重抑制：该状态已提示过")
            return
        }
        promptState.markNotified(promptKey)

        // ---- 9. 通知（方案 A） ----
        LOG.info("[GitIdentityGuard] 发送身份不匹配通知")
        GitIdentityNotifier.notifyMismatch(
            project, repository, login, effectiveEmail, effectiveName,
            cacheEntry.privacyEmail, needEmail, needName, ignoreKey
        )
    }

    /** 汇总仓库全部 Remote 的 fetch/push URL（用于快照与指纹） */
    private fun collectRemoteUrls(repository: GitRepository): Set<String> =
        repository.remotes.flatMap { it.urls + it.pushUrls }.toSet()

    companion object {
        private val LOG = Logger.getInstance(GitIdentityChecker::class.java)
    }
}
