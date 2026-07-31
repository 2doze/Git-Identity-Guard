package io.github.identityguard.service

import io.github.identityguard.github.PrivacyEmailFetchResult
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount

/**
 * 单个 GitHub 账号的隐私邮箱缓存条目。
 *
 * 持久化 Bean：公有无参构造 + 可变属性（供平台 XML 序列化）。
 */
class PrivacyEmailCacheEntry {
    var accountLogin: String = ""
    var serverHost: String = ""
    var privacyEmail: String = ""
    var verifiedEmails: MutableList<String> = mutableListOf()

    /** 是否走了合成回退路径（设置页展示"合成邮箱"状态用） */
    var usedFallback: Boolean = false

    /** 最近一次刷新失败的原因（消息包 key），成功时为 null */
    var lastErrorKey: String? = null
    var lastErrorDetail: String? = null
}

/**
 * 隐私邮箱缓存（设计文档 F1.3 / §3.5）。
 *
 * - **application 级**：IDEA 的 GitHub 账号本身是 application 级，缓存跨项目共享；
 * - 键为 [GithubAccount.getId]（账号唯一标识，与登录名解耦，防止改名/多服务器场景串号）。
 */
@Service
@State(name = "GitIdentityGuardPrivacyEmailCache", storages = [Storage("gitIdentityGuard.xml")])
class PrivacyEmailCacheService : PersistentStateComponent<MutableMap<String, PrivacyEmailCacheEntry>> {

    private var state = mutableMapOf<String, PrivacyEmailCacheEntry>()

    override fun getState(): MutableMap<String, PrivacyEmailCacheEntry> = state

    override fun loadState(loaded: MutableMap<String, PrivacyEmailCacheEntry>) {
        state = loaded
    }

    /** 仅当已成功获取过隐私邮箱时返回条目（失败条目不算可用缓存） */
    @Synchronized
    fun get(account: GithubAccount): PrivacyEmailCacheEntry? =
        state[account.id]?.takeIf { it.privacyEmail.isNotEmpty() }

    @Synchronized
    fun putSuccess(account: GithubAccount, result: PrivacyEmailFetchResult.Success) {
        val entry = state.getOrPut(account.id) { PrivacyEmailCacheEntry() }
        entry.accountLogin = account.name
        entry.serverHost = account.server.host
        entry.privacyEmail = result.privacyEmail
        entry.verifiedEmails = result.verifiedEmails.toMutableList()
        entry.usedFallback = result.usedFallback
        entry.lastErrorKey = null
        entry.lastErrorDetail = null
    }

    /** 记录失败原因（不清空已有可用缓存——离线时仍可用旧值校验） */
    @Synchronized
    fun putFailure(account: GithubAccount, failure: PrivacyEmailFetchResult.Failure) {
        val entry = state.getOrPut(account.id) { PrivacyEmailCacheEntry() }
        entry.accountLogin = account.name
        entry.serverHost = account.server.host
        entry.lastErrorKey = failure.reasonKey
        entry.lastErrorDetail = failure.detail
    }

    /** 设置页展示：全部条目（含失败条目，用于展示错误状态） */
    @Synchronized
    fun allEntries(): Map<String, PrivacyEmailCacheEntry> = LinkedHashMap(state)

    /** 账号登出后清理对应缓存条目（F1.3 prune） */
    @Synchronized
    fun pruneTo(validAccountIds: Set<String>) {
        state.keys.retainAll(validAccountIds)
    }

    /** 凭据变化时使单个账号缓存失效，迫使下轮校验重新获取 */
    @Synchronized
    fun invalidate(account: GithubAccount) {
        state.remove(account.id)
    }

    companion object {
        fun getInstance(): PrivacyEmailCacheService = service()
    }
}
