package io.github.identityguard.service

import io.github.identityguard.util.PromptKey
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * 通知去重状态（project 级、仅内存，设计文档 F4.1 / §3.5）。
 *
 * 去重键是**四元组**（仓库 + 账号 + 当前邮箱 + 目标邮箱）：
 * 同一组合在状态未变化前只提示一次；用户改了邮箱（哪怕还是错的）会产生新组合、可再次提示。
 *
 * 刻意不持久化：IDE 重启后重新提示一次是可接受行为，
 * 避免持久化状态与真实 git 配置漂移后永远不再提示。
 */
@Service(Service.Level.PROJECT)
class PromptStateService {

    private val notified = HashSet<PromptKey>()

    @Synchronized
    fun shouldSuppress(key: PromptKey): Boolean = key in notified

    @Synchronized
    fun markNotified(key: PromptKey) {
        notified.add(key)
    }

    /** 账号集合变化等场景强制清空，允许全量重新提示（F2 第 4 项触发） */
    @Synchronized
    fun clear() {
        notified.clear()
    }

    companion object {
        fun getInstance(project: Project): PromptStateService = project.service()
    }
}
