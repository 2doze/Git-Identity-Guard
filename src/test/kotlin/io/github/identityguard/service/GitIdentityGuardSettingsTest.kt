package io.github.identityguard.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests email acceptance and allowlist normalization in [GitIdentityGuardSettings]. */
class GitIdentityGuardSettingsTest {

    private fun settings(): GitIdentityGuardSettings = GitIdentityGuardSettings()

    @Test
    fun `strict mode never grants a lenient email pass`() {
        val settings = settings()
        settings.lenientPassMode = false
        settings.allowlistEmails = listOf("me@public.com")

        assertFalse(settings.isLenientPass(listOf("me@public.com"), "me@public.com"))
    }

    @Test
    fun `lenient mode accepts a verified account email`() {
        val settings = settings()
        settings.lenientPassMode = true

        assertTrue(settings.isLenientPass(listOf("Me@Public.com"), "me@public.com"))
    }

    @Test
    fun `lenient mode accepts an allowlisted email`() {
        val settings = settings()
        settings.lenientPassMode = true
        settings.allowlistEmails = listOf("special@example.com")

        assertTrue(settings.isLenientPass(emptyList(), " SPECIAL@example.com "))
    }

    @Test
    fun `lenient mode rejects a missing effective email`() {
        val settings = settings()
        settings.lenientPassMode = true

        assertFalse(settings.isLenientPass(listOf("me@public.com"), null))
    }

    @Test
    fun `allowlist emails are normalized when stored`() {
        val settings = settings()

        settings.allowlistEmails = listOf(" A@B.com ", "", "c@d.com")

        assertEquals(listOf("a@b.com", "c@d.com"), settings.allowlistEmails)
    }
}
