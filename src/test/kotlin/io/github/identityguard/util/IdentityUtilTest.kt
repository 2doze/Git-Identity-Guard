package io.github.identityguard.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [IdentityUtil] 单元测试：覆盖 F2.3 比较规则与 F1.1 邮箱格式。
 */
class IdentityUtilTest {

    @Test
    fun `emails match after trimming whitespace and ignoring case`() {
        assertTrue(IdentityUtil.emailsMatch(" 12345+Alice@users.noreply.github.com ", "12345+alice@users.noreply.github.com"))
    }

    @Test
    fun `names match after normalization and reject missing or different values`() {
        assertTrue(IdentityUtil.namesMatch(" 2doze ", "2DOZE"))
        assertFalse(IdentityUtil.namesMatch(null, "2doze"))
        assertFalse(IdentityUtil.namesMatch("cuizhy", "2doze"))
    }

    @Test
    fun `missing emails never match`() {
        assertFalse(IdentityUtil.emailsMatch(null, "a@b.com"))
        assertFalse(IdentityUtil.emailsMatch("a@b.com", null))
        assertFalse(IdentityUtil.emailsMatch(null, null))
    }

    @Test
    fun `canonical email normalizes missing blank and mixed case values`() {
        assertEquals("", IdentityUtil.canonicalEmail(null))
        assertEquals("", IdentityUtil.canonicalEmail("  "))
        assertEquals("a@b.com", IdentityUtil.canonicalEmail(" A@B.COM "))
    }

    @Test
    fun `noreply detection accepts legacy and modern GitHub formats`() {
        assertTrue(IdentityUtil.isNoreplyEmail("12345+alice@users.noreply.github.com"))
        assertTrue(IdentityUtil.isNoreplyEmail("alice@users.noreply.github.com"))
        assertFalse(IdentityUtil.isNoreplyEmail("alice@gmail.com"))
        assertFalse(IdentityUtil.isNoreplyEmail(null))
    }

    @Test
    fun `noreply synthesis uses the modern GitHub format`() {
        assertEquals(
            "12345+alice@users.noreply.github.com",
            IdentityUtil.synthesizeNoreplyEmail(12345, "Alice")
        )
    }
}
