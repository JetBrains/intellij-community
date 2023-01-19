package org.intellij.plugins.markdown.injection

import com.intellij.idea.TestFor
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import junit.framework.TestCase
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser
import org.junit.Test

@TestFor(issues = ["IDEA-299029"])
class CodeFenceInfoStringResolveTest : LightPlatformCodeInsightTestCase() {
  @Test
  fun `test markdown is resolved`() = assertInfoStringIsResolved("markdown", "markdown")

  @Test
  fun `test markdown text is resolved to just markdown`() {
    assertInfoStringIsResolved("markdown text", "markdown")
  }

  @Test
  fun `test markdown with suffix is resolved`() = assertInfoStringIsResolved("markdown live", "markdown")

  @Test
  fun `test plain text is resolved`() = assertInfoStringIsResolved("text", "text")

  @Test
  fun `test json is resolved`() = assertInfoStringIsResolved("json", "json")

  @Test
  fun `test json lines are resolved`() = assertInfoStringIsResolved("json lines", "json lines")

  @Test
  fun `test json lines with suffix are resolved`() = assertInfoStringIsResolved("json lines live", "json lines")

  @Test
  fun `test json awesome lines are resolved to just json`() {
    assertInfoStringIsResolved("json awesome lines", "json")
  }

  private fun resolveInfoString(infoString: String): String? {
    val resolved = CodeFenceLanguageGuesser.guessLanguageForInjection(infoString)
    return resolved?.id?.lowercase()
  }

  private fun assertInfoStringIsResolved(infoString: String, expectedId: String?) {
    val id = resolveInfoString(infoString)
    TestCase.assertEquals(expectedId, id)
  }
}
