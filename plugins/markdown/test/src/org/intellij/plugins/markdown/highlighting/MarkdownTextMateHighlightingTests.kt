// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.highlighting

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.PlatformTestUtil
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.jetbrains.plugins.textmate.TextMateLanguage
import org.jetbrains.plugins.textmate.TextMateService

class MarkdownTextMateHighlightingTests : LightPlatformCodeInsightTestCase() {
  override fun setUp() {
    super.setUp()
    TextMateService.getInstance().reloadEnabledBundles()
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  fun `test textmate only fence injects textmate with matching extension`() {
    configureFromFileText("test.md", """
      ```bicep
      resource <caret>account 'Microsoft.Storage/storageAccounts@2022-09-01' = {}
      ```
    """.trimIndent())

    val guessed = CodeFenceLanguageGuesser.guessLanguageWithExtensionForInjection("bicep")
    assertNotNull(guessed)
    assertSame(TextMateLanguage.LANGUAGE, guessed!!.first)
    assertEquals("bicep", guessed.second)

    assertSame(TextMateLanguage.LANGUAGE, file.language)
    assertTrue(file.name.endsWith(".bicep"))
  }

  fun `test native language fence is not handled by textmate`() {
    configureFromFileText("test.md", """
      ```xml
      <div><caret></div>
      ```
    """.trimIndent())

    assertFalse(file.language.isKindOf(MarkdownLanguage.INSTANCE))
    assertNotSame(TextMateLanguage.LANGUAGE, file.language)
  }
}
