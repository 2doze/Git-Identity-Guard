package io.github.identityguard.service

import io.github.identityguard.util.IgnoreKey
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * 忽略列表（project 级持久化，设计文档 F4.1）。
 *
 * 忽略键是**二元组**（仓库标识 + 命中账号），与具体邮箱值无关：
 * 用户选择"忽略该仓库"后，无论邮箱如何变化都不再针对该匹配关系提示，
 * 直到用户在设置页手动移除。
 *
 * 仓库标识 = 仓库根路径 + Remote 指纹（§5 注意事项 3）：
 * 仓库重建或 Remote 整体更换后指纹变化，旧忽略记录不会被新仓库错误继承。
 */
@Service(Service.Level.PROJECT)
@State(name = "GitIdentityGuardIgnoreList", storages = [Storage("gitIdentityGuard.xml")])
class IgnoreListService : PersistentStateComponent<IgnoreListService.IgnoreState> {

    /** 持久化条目（Bean 要求公有无参构造 + 可变属性） */
    class IgnoreEntry {
        var repoRootPath: String = ""
        var remoteFingerprint: String = ""
        var accountLogin: String = ""

        fun toKey(): IgnoreKey = IgnoreKey(repoRootPath, remoteFingerprint, accountLogin)
    }

    class IgnoreState {
        var entries: MutableList<IgnoreEntry> = mutableListOf()
    }

    private var state = IgnoreState()

    override fun getState(): IgnoreState = state

    override fun loadState(loaded: IgnoreState) {
        state = loaded
    }

    @Synchronized
    fun isIgnored(key: IgnoreKey): Boolean = state.entries.any { it.toKey() == key }

    @Synchronized
    fun ignore(key: IgnoreKey) {
        if (isIgnored(key)) return
        state.entries.add(IgnoreEntry().apply {
            repoRootPath = key.repoRootPath
            remoteFingerprint = key.remoteFingerprint
            accountLogin = key.accountLogin
        })
    }

    @Synchronized
    fun remove(key: IgnoreKey) {
        state.entries.removeAll { it.toKey() == key }
    }

    /** 设置页展示用快照 */
    @Synchronized
    fun entries(): List<IgnoreEntry> = state.entries.map { it }

    companion object {
        fun getInstance(project: Project): IgnoreListService = project.service()
    }
}
