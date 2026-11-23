package org.intellij.plugins.markdown.injection

import com.intellij.idea.TestFor
import com.intellij.testFramework.junit5.TestApplication
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestFor(issues = ["IDEA-299029"])
@TestApplication
class CodeFenceInfoStringResolveTest {
  @Test
  fun `test markdown is resolved`(): Unit = assertInfoStringIsResolved("markdown", "markdown")

  @Test
  fun `test markdown text is resolved to just markdown`() {
    assertInfoStringIsResolved("markdown text", "markdown")
  }

  @Test
  fun `test markdown with suffix is resolved`(): Unit = assertInfoStringIsResolved("markdown live", "markdown")

  @Test
  fun `test plain text is resolved`(): Unit = assertInfoStringIsResolved("text", "text")

  @Test
  fun `test json is resolved`(): Unit = assertInfoStringIsResolved("json", "json")

  @Test
  fun `test json lines are resolved`(): Unit = assertInfoStringIsResolved("json lines", "json lines")

  @Test
  fun `test json lines with suffix are resolved`(): Unit = assertInfoStringIsResolved("json lines live", "json lines")

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
    assertEquals(expectedId, id)
    assertEquals(expectedId, CodeFenceLanguageGuesser.guessLanguageForExecution(infoString)?.id?.lowercase())
  }
}
