package io.github.identityguard.listener

import io.github.identityguard.check.GitIdentityChecker
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener

/**
 * Git 仓库变化监听器（F2 第 2 项触发时机，经 plugin.xml projectListeners 注册）。
 *
 * 该事件在任何仓库更新（fetch、status 刷新、分支切换等）时都会触发，频率很高；
 * 节流（Remote 快照未变化则跳过完整校验）在校验流程内部完成（F2.4），
 * 因此这里无条件转发即可，不需要自己判断"是否 Remote 变化"。
 */
class GitRepoCheckListener : GitRepositoryChangeListener {

    override fun repositoryChanged(repository: GitRepository) {
        GitIdentityChecker(repository.project).checkRepository(repository)
    }
}
