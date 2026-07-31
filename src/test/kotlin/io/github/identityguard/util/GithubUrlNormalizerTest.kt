package io.github.identityguard.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [GithubUrlNormalizer] 单元测试：覆盖设计文档 F2.1 所列全部形式与典型反例。
 */
class GithubUrlNormalizerTest {

    // ---------- 受支持的形式 ----------

    @Test
    fun `normalizes standard HTTPS URL`() {
        assertEquals(
            GithubRepoId("alice", "demo"),
            GithubUrlNormalizer.normalize("https://github.com/alice/demo")
        )
    }

    @Test
    fun `normalizes HTTPS URL with git suffix`() {
        assertEquals(
            GithubRepoId("alice", "demo"),
            GithubUrlNormalizer.normalize("https://github.com/alice/demo.git")
        )
    }

    @Test
    fun `normalizes HTTPS URL with trailing slash and whitespace`() {
        assertEquals(
            GithubRepoId("alice", "demo"),
            GithubUrlNormalizer.normalize("  https://github.com/alice/demo/  ")
        )
    }

    @Test
    fun `normalizes HTTP URL`() {
        assertEquals(
            GithubRepoId("alice", "demo"),
            GithubUrlNormalizer.normalize("http://github.com/alice/demo.git")
        )
    }

    @Test
    fun `normalizes SCP style URL with git suffix`() {
        assertEquals(
            GithubRepoId("alice", "demo"),
            GithubUrlNormalizer.normalize("git@github.com:alice/demo.git")
        )
    }

    @Test
    fun `normalizes SCP style URL without git suffix`() {
        assertEquals(
            GithubRepoId("alice", "demo"),
            GithubUrlNormalizer.normalize("git@github.com:alice/demo")
        )
    }

    @Test
    fun `normalizes explicit SSH URL`() {
        assertEquals(
            GithubRepoId("alice", "demo"),
            GithubUrlNormalizer.normalize("ssh://git@github.com/alice/demo.git")
        )
    }

    @Test
    fun `normalizes explicit SSH URL with port`() {
        assertEquals(
            GithubRepoId("alice", "demo"),
            GithubUrlNormalizer.normalize("ssh://git@github.com:22/alice/demo.git")
        )
    }

    @Test
    fun `normalizes host owner and repository to lowercase`() {
        assertEquals(
            GithubRepoId("alice", "demo"),
            GithubUrlNormalizer.normalize("https://GitHub.COM/Alice/Demo.GIT")
        )
    }

    @Test
    fun `accepts www host prefix`() {
        assertEquals(
            GithubRepoId("alice", "demo"),
            GithubUrlNormalizer.normalize("https://www.github.com/alice/demo")
        )
    }

    // ---------- 反例（非目标 / 非法输入） ----------

    @Test
    fun `rejects Gist URLs`() {
        assertNull(GithubUrlNormalizer.normalize("https://gist.github.com/alice/abc123"))
    }

    @Test
    fun `rejects owner-only paths`() {
        assertNull(GithubUrlNormalizer.normalize("https://github.com/alice"))
    }

    @Test
    fun `rejects paths with extra segments`() {
        assertNull(GithubUrlNormalizer.normalize("https://github.com/alice/demo/wiki"))
    }

    @Test
    fun `rejects git protocol URLs`() {
        assertNull(GithubUrlNormalizer.normalize("git://github.com/alice/demo.git"))
    }

    @Test
    fun `rejects SSH config host aliases`() {
        assertNull(GithubUrlNormalizer.normalize("git@gh:alice/demo.git"))
    }

    @Test
    fun `rejects GitHub Enterprise hosts`() {
        assertNull(GithubUrlNormalizer.normalize("https://github.example.com/alice/demo.git"))
        assertNull(GithubUrlNormalizer.normalize("git@github.example.com:alice/demo.git"))
    }

    @Test
    fun `rejects empty and blank values`() {
        assertNull(GithubUrlNormalizer.normalize(""))
        assertNull(GithubUrlNormalizer.normalize("   "))
    }

    @Test
    fun `rejects bare paths without a protocol or colon`() {
        assertNull(GithubUrlNormalizer.normalize("github.com/alice/demo"))
    }
}
