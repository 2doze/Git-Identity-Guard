package io.github.identityguard.service

import io.github.identityguard.util.IgnoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests repository ignore lifecycle and remote fingerprint isolation. */
class IgnoreListServiceTest {

    private val ignoreKey = IgnoreKey("/repo", "1-abcd", "alice")

    @Test
    fun `ignored repository is recognized and can be removed`() {
        val service = IgnoreListService()

        service.ignore(ignoreKey)
        assertTrue(service.isIgnored(ignoreKey))

        service.remove(ignoreKey)
        assertFalse(service.isIgnored(ignoreKey))
    }

    @Test
    fun `ignoring the same repository twice is idempotent`() {
        val service = IgnoreListService()

        service.ignore(ignoreKey)
        service.ignore(ignoreKey)

        assertEquals(1, service.entries().size)
    }

    @Test
    fun `changed remote fingerprint does not inherit an existing ignore`() {
        val service = IgnoreListService()
        service.ignore(ignoreKey)

        assertFalse(service.isIgnored(IgnoreKey("/repo", "1-ffff", "alice")))
    }
}
