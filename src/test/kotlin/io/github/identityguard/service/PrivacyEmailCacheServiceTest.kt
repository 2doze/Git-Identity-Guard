package io.github.identityguard.service

import io.github.identityguard.github.PrivacyEmailFetchResult
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PrivacyEmailCacheService] 单元测试：覆盖可用性、TTL 过期与失败冷却回退语义（F1.3）。
 */
class PrivacyEmailCacheServiceTest {

    private val service = PrivacyEmailCacheService()
    private val account = GithubAccount("2doze", GithubServerPath.DEFAULT_SERVER, "acc-1")

    @After
    fun restoreTtl() {
        PrivacyEmailCacheService.ttlMillis = PrivacyEmailCacheService.DEFAULT_TTL_MILLIS
    }

    private fun success() = PrivacyEmailFetchResult.Success(
        "12345+2doze@users.noreply.github.com",
        listOf("2doze@gmail.com"),
        usedFallback = false
    )

    @Test
    fun `put success makes the entry available and fresh`() {
        service.putSuccess(account, success())

        val entry = service.get(account)
        assertEquals("12345+2doze@users.noreply.github.com", entry?.privacyEmail)
        assertFalse(service.shouldRefresh(account))
    }

    @Test
    fun `no entry means a refresh is needed`() {
        assertNull(service.get(account))
        assertTrue(service.shouldRefresh(account))
    }

    @Test
    fun `entry expires after the ttl passes`() {
        service.putSuccess(account, success())
        PrivacyEmailCacheService.ttlMillis = 10

        // 刚写入：未过期
        assertFalse(service.shouldRefresh(account))
        Thread.sleep(30)
        // TTL 过后：需要刷新，但旧值仍可用（供失败回退）
        assertTrue(service.shouldRefresh(account))
        assertEquals("12345+2doze@users.noreply.github.com", service.get(account)?.privacyEmail)
    }

    @Test
    fun `failure keeps the stale value and cools down retries`() {
        service.putSuccess(account, success())
        PrivacyEmailCacheService.ttlMillis = 10
        Thread.sleep(30)
        assertTrue(service.shouldRefresh(account))

        // 刷新失败：旧值保留（回退用），但尝试时间被推进 —— TTL 内不再重试
        val fetchedAtBefore = service.get(account)?.fetchedAt
        service.putFailure(account, PrivacyEmailFetchResult.Failure("error.network", "offline"))

        val entry = service.get(account)
        assertEquals("12345+2doze@users.noreply.github.com", entry?.privacyEmail)
        assertEquals(fetchedAtBefore, entry?.fetchedAt)
        assertEquals("error.network", entry?.lastErrorKey)
        assertFalse(service.shouldRefresh(account))
    }

    @Test
    fun `failure without any previous value leaves no usable entry and cools down`() {
        service.putFailure(account, PrivacyEmailFetchResult.Failure("error.auth", "token unavailable"))

        assertNull(service.get(account))
        // 失败同样推进尝试时间：无旧值也不高频重试（每天最多一次自动刷新）
        assertFalse(service.shouldRefresh(account))
    }

    @Test
    fun `prune removes entries of logged out accounts`() {
        service.putSuccess(account, success())
        val other = GithubAccount("someone", GithubServerPath.DEFAULT_SERVER, "acc-2")
        service.putSuccess(other, success())

        service.pruneTo(setOf(account.id))

        assertTrue(service.get(account) != null)
        assertNull(service.get(other))
    }
}
