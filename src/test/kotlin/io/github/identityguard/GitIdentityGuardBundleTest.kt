package io.github.identityguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.PropertyResourceBundle
import java.util.ResourceBundle

/** Verifies that every supported locale provides a complete message bundle. */
class GitIdentityGuardBundleTest {

    @Test
    fun `English and Simplified Chinese bundles define identical keys`() {
        assertEquals(loadBundle(DEFAULT_BUNDLE).keys, loadBundle(CHINESE_BUNDLE).keys)
    }

    @Test
    fun `default bundle is English and localized bundle is Simplified Chinese`() {
        val english = loadBundle(Locale.ENGLISH)
        val chinese = loadBundle(Locale.SIMPLIFIED_CHINESE)

        assertEquals("Git Identity Mismatch", english.getString("notification.title"))
        assertEquals("Git 身份不匹配", chinese.getString("notification.title"))
        assertFalse(english.keySet().map(english::getString).any(HAN_CHARACTER::containsMatchIn))
    }

    @Test
    fun `localized messages preserve every MessageFormat argument`() {
        val english = loadBundle(DEFAULT_BUNDLE)
        val chinese = loadBundle(CHINESE_BUNDLE)

        for (key in english.keys) {
            assertEquals(key, messageArguments(english.getValue(key)), messageArguments(chinese.getValue(key)))
        }
    }

    private fun loadBundle(resourceName: String): Map<String, String> {
        val stream = requireNotNull(javaClass.classLoader.getResourceAsStream(resourceName)) {
            "Missing resource bundle: $resourceName"
        }
        return InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
            val bundle = PropertyResourceBundle(reader)
            bundle.keys.asSequence().associateWith(bundle::getString)
        }
    }

    /** Disables fallback to the test JVM locale so each requested locale is verified independently. */
    private fun loadBundle(locale: Locale): ResourceBundle =
        ResourceBundle.getBundle(BUNDLE_BASE_NAME, locale, javaClass.classLoader, NO_LOCALE_FALLBACK)

    companion object {
        private const val BUNDLE_BASE_NAME = "messages.GitIdentityGuardBundle"
        private const val DEFAULT_BUNDLE = "messages/GitIdentityGuardBundle.properties"
        private const val CHINESE_BUNDLE = "messages/GitIdentityGuardBundle_zh_CN.properties"
        private val HAN_CHARACTER = Regex("\\p{IsHan}")
        private val MESSAGE_ARGUMENT = Regex("\\{(\\d+)}")
        private val NO_LOCALE_FALLBACK =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES)

        private fun messageArguments(message: String): Set<String> =
            MESSAGE_ARGUMENT.findAll(message).map { it.groupValues[1] }.toSet()
    }
}
