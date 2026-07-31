package io.github.identityguard.service

import io.github.identityguard.util.PromptKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests notification de-duplication based on the complete prompt identity. */
class PromptStateServiceTest {

    private fun promptKey(currentEmail: String) = PromptKey(
        repoRootPath = "/repo",
        remoteFingerprint = "1-abcd",
        accountLogin = "alice",
        currentEmail = currentEmail,
        targetEmail = "12345+alice@users.noreply.github.com"
    )

    @Test
    fun `same prompt key is suppressed after notification`() {
        val service = PromptStateService()
        val key = promptKey("work@company.com")

        assertFalse(service.shouldSuppress(key))
        service.markNotified(key)
        assertTrue(service.shouldSuppress(key))
    }

    @Test
    fun `changed current email creates a new prompt key`() {
        val service = PromptStateService()
        service.markNotified(promptKey("work@company.com"))

        assertFalse(service.shouldSuppress(promptKey("other@wrong.com")))
    }
}
