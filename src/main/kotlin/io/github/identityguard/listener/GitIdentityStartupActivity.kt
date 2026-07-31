package io.github.identityguard.listener

import io.github.identityguard.check.GitIdentityChecker
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 项目启动入口（F2 第 1 项触发时机）：项目打开后尝试首次校验。
 * Git 仓库初始化是异步的，此刻仓库列表为空也没关系——
 * 仓库加载完成会触发 [GitRepoCheckListener] 补上（F2 竞态说明）。
 *
 * 关于 F2 第 4 项"账号集合变化后重校验"：
 * 2025.3 移除了 GithubAuthenticationManager.addListener，且无等价公开事件，
 * 改为以下等效覆盖方式（见 GitIdentityChecker 的延迟 prune 与设置页刷新触发）：
 * 1. 每次校验都重新读取当前账号集合并延迟清理过期缓存——登出账号立即失效；
 * 2. 新登录账号的命中在下一个仓库事件（fetch/status 等高频事件）时被捕获；
 * 3. 设置页"手动刷新"后会主动强制全量重校验。
 */
class GitIdentityStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        LOG.info("[GitIdentityGuard] 项目启动入口触发: ${project.name}")
        // 首次校验放到 IO 调度器：内部有外部 git 调用（§3.4）
        withContext(Dispatchers.IO) {
            GitIdentityChecker(project).checkAllRepositories(force = true)
        }
    }

    companion object {
        private val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(GitIdentityStartupActivity::class.java)
    }
}
