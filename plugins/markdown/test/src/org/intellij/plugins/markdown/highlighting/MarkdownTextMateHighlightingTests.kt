// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.highlighting

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.jetbrains.plugins.textmate.TextMateLanguage
import org.jetbrains.plugins.textmate.TextMateService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class MarkdownTextMateHighlightingTests {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @BeforeEach
  fun setUp(): Unit = timeoutRunBlocking {
    TextMateService.getInstance().reloadEnabledBundles()
    withContext(Dispatchers.EDT) {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
  }

  @Test
  fun `test textmate only fence injects textmate with matching extension`(): Unit = timeoutRunBlocking {
    myFixture.configureByText("test.md", """
      ```bicep
      resource <caret>account 'Microsoft.Storage/storageAccounts@2022-09-01' = {}
      ```
    """.trimIndent())

    val guessed = CodeFenceLanguageGuesser.guessLanguageWithExtensionForInjection("bicep")
    assertNotNull(guessed)
    assertSame(TextMateLanguage.LANGUAGE, guessed!!.first)
    assertEquals("bicep", guessed.second)

    readAction {
      assertSame(TextMateLanguage.LANGUAGE, myFixture.file.language)
      assertTrue(myFixture.file.name.endsWith(".bicep"))
    }
  }

  @Test
  fun `test native language fence is not handled by textmate`(): Unit = timeoutRunBlocking {
    myFixture.configureByText("test.md", """
      ```xml
      <div><caret></div>
      ```
    """.trimIndent())

    readAction {
      assertFalse(myFixture.file.language.isKindOf(MarkdownLanguage.INSTANCE))
      assertNotSame(TextMateLanguage.LANGUAGE, myFixture.file.language)
    }
  }
}
