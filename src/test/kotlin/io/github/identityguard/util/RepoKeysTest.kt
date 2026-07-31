package io.github.identityguard.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * [RepoKeys] 指纹逻辑测试（F4.1）：
 * 指纹应与 URL 顺序无关、随 Remote 实质变化而变化。
 */
class RepoKeysTest {

    @Test
    fun `fingerprint is independent of URL order`() {
        val a = RepoKeys.remoteFingerprint(listOf("git@github.com:alice/one.git", "https://github.com/alice/two.git"))
        val b = RepoKeys.remoteFingerprint(listOf("https://github.com/alice/two.git", "git@github.com:alice/one.git"))
        assertEquals(a, b)
    }

    @Test
    fun `equivalent remote URL forms have the same fingerprint`() {
        // 带不带 .git 后缀是同一 Remote 的等价写法
        val a = RepoKeys.remoteFingerprint(listOf("git@github.com:alice/demo.git"))
        val b = RepoKeys.remoteFingerprint(listOf("https://github.com/alice/demo"))
        assertEquals(a, b)
    }

    @Test
    fun `fingerprint changes when the remote repository changes`() {
        val a = RepoKeys.remoteFingerprint(listOf("git@github.com:alice/demo.git"))
        val b = RepoKeys.remoteFingerprint(listOf("git@github.com:bob/other.git"))
        assertNotEquals(a, b)
    }

    @Test
    fun `missing GitHub remotes return the sentinel fingerprint`() {
        assertEquals("no-github-remote", RepoKeys.remoteFingerprint(listOf("git@gitlab.com:alice/demo.git")))
        assertEquals("no-github-remote", RepoKeys.remoteFingerprint(emptyList()))
    }
}
