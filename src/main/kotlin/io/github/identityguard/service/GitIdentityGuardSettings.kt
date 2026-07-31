package io.github.identityguard.service

import io.github.identityguard.util.IdentityUtil
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/**
 * 插件全局设置（application 级，设计文档 §4）。
 *
 * 默认值取舍说明：
 * - preCommitCheckEnabled 默认 false：通知（方案 A）是主通道、干扰小；
 *   Commit 前强校验（方案 B）属于用户可选的更严格模式；
 * - lenientPassMode 默认 false：严格模式是文档 F2.3 的基线行为，
 *   宽松放行（F3.2）是为"故意使用公开已验证邮箱"的用户提供的缓解手段。
 */
@Service
@State(name = "GitIdentityGuardSettings", storages = [Storage("gitIdentityGuard.xml")])
class GitIdentityGuardSettings : PersistentStateComponent<GitIdentityGuardSettings.State> {

    class State {
        /**
         * 是否启用 Commit 前校验（F2 第 3 项）。
         * 默认开启：通知（方案 A）仅为建议、不拦截提交；只有本项能真正阻止
         * 用错误邮箱落盘提交（方案 B 弹窗）。用户可在设置页关闭。
         */
        var preCommitCheckEnabled: Boolean = true

        /** 是否在通知内容中展示当前生效邮箱（§4 高级项） */
        var showCurrentEmailInNotification: Boolean = true

        /** 宽松模式：该账号 verified 邮箱与白名单邮箱也视为放行（F3.2） */
        var lenientPassMode: Boolean = false

        /** 检测到不匹配时自动开启 Commit 前校验（§4 高级项） */
        var autoEnablePreCommitOnMismatch: Boolean = false

        /** 用户手动维护的放行邮箱（存储规范化小写形式） */
        var allowlistEmails: MutableList<String> = mutableListOf()
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(loaded: State) {
        myState = loaded
    }

    var preCommitCheckEnabled: Boolean
        get() = myState.preCommitCheckEnabled
        set(value) { myState.preCommitCheckEnabled = value }

    var showCurrentEmailInNotification: Boolean
        get() = myState.showCurrentEmailInNotification
        set(value) { myState.showCurrentEmailInNotification = value }

    var lenientPassMode: Boolean
        get() = myState.lenientPassMode
        set(value) { myState.lenientPassMode = value }

    var autoEnablePreCommitOnMismatch: Boolean
        get() = myState.autoEnablePreCommitOnMismatch
        set(value) { myState.autoEnablePreCommitOnMismatch = value }

    var allowlistEmails: List<String>
        get() = myState.allowlistEmails.toList()
        set(value) { myState.allowlistEmails = value.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toMutableList() }

    /**
     * 宽松模式放行判定（F3.2）：
     * 生效邮箱命中该账号任一 verified 邮箱或用户白名单时不触发提示。
     * 严格模式（默认）下恒返回 false。
     */
    fun isLenientPass(verifiedEmails: List<String>, effectiveEmail: String?): Boolean {
        if (!myState.lenientPassMode) return false
        val canonical = IdentityUtil.canonicalEmail(effectiveEmail)
        if (canonical.isEmpty()) return false
        if (canonical in myState.allowlistEmails) return true
        return verifiedEmails.any { IdentityUtil.canonicalEmail(it) == canonical }
    }

    companion object {
        fun getInstance(): GitIdentityGuardSettings = service()
    }
}
