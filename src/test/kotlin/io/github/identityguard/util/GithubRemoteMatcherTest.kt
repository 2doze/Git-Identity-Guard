package io.github.identityguard.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [GithubRemoteMatcher] 单元测试：覆盖 F2.2 保护边界、F2.3 多命中优先级与大小写规则。
 */
class GithubRemoteMatcherTest {

    @Test
    fun `matches when remote owner equals an account login`() {
        val remotes = listOf(RemoteRef("origin", listOf("git@github.com:alice/demo.git")))
        val matched = GithubRemoteMatcher.findMatchedAccounts(remotes, listOf("alice"))
        assertEquals(1, matched.size)
        assertEquals("alice/demo", matched["alice"]!!.repoId.toString())
    }

    @Test
    fun `matches account logins case insensitively`() {
        // Remote 中大小写与账号登录名不一致时不应漏判（GitHub 用户名大小写不敏感）
        val remotes = listOf(RemoteRef("origin", listOf("git@github.com:ALICE/demo.git")))
        val matched = GithubRemoteMatcher.findMatchedAccounts(remotes, listOf("Alice"))
        assertTrue("alice" in matched)
    }

    @Test
    fun `does not match owners without a corresponding account`() {
        val remotes = listOf(RemoteRef("origin", listOf("git@github.com:acme-org/demo.git")))
        val matched = GithubRemoteMatcher.findMatchedAccounts(remotes, listOf("alice"))
        assertTrue(matched.isEmpty())
    }

    @Test
    fun `prefers origin when multiple remotes match the same account`() {
        val remotes = listOf(
            RemoteRef("upstream", listOf("https://github.com/alice/demo.git")),
            RemoteRef("origin", listOf("git@github.com:alice/demo-fork.git")),
        )
        val matched = GithubRemoteMatcher.findMatchedAccounts(remotes, listOf("alice"))
        // origin 应覆盖传入顺序更靠前的 upstream
        assertEquals("origin", matched["alice"]!!.remoteName)
        assertEquals("alice/demo-fork", matched["alice"]!!.repoId.toString())
    }

    @Test
    fun `returns deterministic matches for multiple accounts`() {
        val remotes = listOf(
            RemoteRef("origin", listOf("https://github.com/bob/repo-b.git")),
            RemoteRef("second", listOf("https://github.com/alice/repo-a.git")),
        )
        val matched = GithubRemoteMatcher.findMatchedAccounts(remotes, listOf("alice", "bob"))
        assertEquals(setOf("alice", "bob"), matched.keys)
        // origin 命中的 bob 应排在迭代最前
        assertEquals("bob", matched.keys.first())
    }

    @Test
    fun `matches URLs merged from fetch and push remotes`() {
        // 模拟 fetch URL 不是 GitHub、push URL 是 GitHub 的场景
        val remotes = listOf(
            RemoteRef("origin", listOf("https://internal.example.com/alice/demo.git", "git@github.com:alice/demo.git"))
        )
        val matched = GithubRemoteMatcher.findMatchedAccounts(remotes, listOf("alice"))
        assertEquals(1, matched.size)
    }

    @Test
    fun `returns no matches for empty remotes or accounts`() {
        assertTrue(GithubRemoteMatcher.findMatchedAccounts(emptyList(), listOf("alice")).isEmpty())
        assertTrue(
            GithubRemoteMatcher.findMatchedAccounts(
                listOf(RemoteRef("origin", listOf("git@github.com:alice/demo.git"))),
                emptyList()
            ).isEmpty()
        )
    }
}
