// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.injection

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.InjectableLanguage
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.jetbrains.plugins.textmate.TextMateLanguage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

@TestApplication
internal class CodeFenceLanguageGuesserTest {
  @Test
  fun `language lookup resolves aliases with whitespace`() {
    for (alias in listOf("markdown", "md", "Markdown", "MD")) {
      for (suffix in listOf("", " title=demo live", "\ttitle=demo", " \ttitle=demo\tlive")) {
        val infoString = " \t$alias$suffix \t"
        assertSame(MarkdownLanguage.INSTANCE, CodeFenceLanguageGuesser.findLanguage(infoString), infoString)
      }
    }
    assertNull(CodeFenceLanguageGuesser.findLanguage(" \tunknown-fence\ttitle=demo "))
  }

  @Test
  fun `language lookup preserves language names with spaces`(@TestDisposable disposable: Disposable) {
    val expected = object : Language("Code Fence Test"), InjectableLanguage {}
    Disposer.register(disposable) {
      expected.unregisterLanguage(DefaultPluginDescriptor("org.intellij.plugins.markdown"))
    }

    for (suffix in listOf("", " title=demo live", "\ttitle=demo", " \ttitle=demo\tlive")) {
      val infoString = " \tCode Fence Test$suffix \t"
      assertSame(expected, CodeFenceLanguageGuesser.findLanguage(infoString), infoString)
    }
  }

  @Test
  fun `PowerShell aliases resolve without the language plugin`() {
    assertNull(Language.findLanguageByID("PowerShell"))

    for (alias in listOf("powershell", "posh", "pwsh", "PowerShell", "POSH", "PwSh")) {
      for (suffix in listOf("", " title=demo live", "\ttitle=demo")) {
        val infoString = " \t$alias$suffix "
        assertEquals("PowerShell", CodeFenceLanguageGuesser.guessLanguageForExecution(infoString)?.id, infoString)
      }
    }
    assertNull(Language.findLanguageByID("PowerShell"))
  }

  @Test
  fun `PowerShell aliases do not match other fence names`() {
    for (infoString in listOf("power", "powershell-script", "posh1", "pwshx", "shell", "")) {
      assertNotEquals("PowerShell", CodeFenceLanguageGuesser.guessLanguageForExecution(infoString)?.id, infoString)
    }
  }

  @Test
  fun `PowerShell execution preserves TextMate injection`() {
    for (infoString in listOf("powershell", "posh", "pwsh", "powershell title=demo")) {
      assertEquals("PowerShell", CodeFenceLanguageGuesser.guessLanguageForExecution(infoString)?.id, infoString)
      assertSame(TextMateLanguage.LANGUAGE, CodeFenceLanguageGuesser.guessLanguageForInjection(infoString), infoString)
      assertSame(TextMateLanguage.LANGUAGE, CodeFenceLanguageGuesser.guessLanguageWithExtensionForInjection(infoString)?.first, infoString)
    }
  }

  @Test
  fun `PowerShell execution uses the installed language`(@TestDisposable disposable: Disposable) {
    val expected = object : Language("PowerShell"), InjectableLanguage {}
    Disposer.register(disposable) {
      expected.unregisterLanguage(DefaultPluginDescriptor("org.intellij.plugins.markdown"))
    }

    for (infoString in listOf("powershell", "posh", "pwsh", "powershell title=demo", "\tPwSh\ttitle=demo ")) {
      assertSame(expected, CodeFenceLanguageGuesser.guessLanguageForExecution(infoString), infoString)
      assertSame(expected, CodeFenceLanguageGuesser.guessLanguageForInjection(infoString), infoString)
    }
  }

  @Test
  fun `PowerShell fallback preserves an installed language with different casing`(@TestDisposable disposable: Disposable) {
    val expected = object : Language("Powershell"), InjectableLanguage {}
    Disposer.register(disposable) {
      expected.unregisterLanguage(DefaultPluginDescriptor("org.intellij.plugins.markdown"))
    }

    for (infoString in listOf("powershell", "POSH", "PwSh", "powershell title=demo")) {
      assertSame(expected, CodeFenceLanguageGuesser.guessLanguageForExecution(infoString), infoString)
      assertSame(expected, CodeFenceLanguageGuesser.guessLanguageForInjection(infoString), infoString)
    }
  }

  @Test
  fun `PowerShell fallback preserves a custom language provider`(@TestDisposable disposable: Disposable) {
    val expected = object : Language("VendorPowerShell", false), InjectableLanguage {}
    val provider = object : CodeFenceLanguageProvider {
      override fun getLanguageByInfoString(value: String): Language = expected

      override fun getCompletionVariantsForInfoString(parameters: CompletionParameters): List<LookupElement> = emptyList()
    }
    CodeFenceLanguageProvider.EP_NAME.point.registerExtension(provider, disposable)

    for (infoString in listOf("powershell", "POSH", "PwSh", "powershell title=demo", "pwsh\ttitle=demo")) {
      assertSame(expected, CodeFenceLanguageGuesser.guessLanguageForExecution(infoString), infoString)
      assertSame(expected, CodeFenceLanguageGuesser.guessLanguageForInjection(infoString), infoString)
      assertSame(expected, CodeFenceLanguageGuesser.guessLanguageWithExtensionForInjection(infoString)?.first, infoString)
    }
  }

  @Test
  fun `execution accepts non-injectable languages while injection skips them`(@TestDisposable disposable: Disposable) {
    val infoStrings = listOf("markdown", "markdown title=demo")
    val language = object : Language("CodeFenceExecutionTest", false) {}
    val provider = object : CodeFenceLanguageProvider {
      override fun getLanguageByInfoString(value: String): Language? =
        language.takeIf { value in infoStrings }

      override fun getCompletionVariantsForInfoString(parameters: CompletionParameters): List<LookupElement> = emptyList()
    }
    ExtensionTestUtil.maskExtensions(
      CodeFenceLanguageProvider.EP_NAME,
      listOf(provider) + CodeFenceLanguageGuesser.customProviders,
      disposable,
    )

    for (infoString in infoStrings) {
      assertSame(MarkdownLanguage.INSTANCE, CodeFenceLanguageGuesser.guessLanguageForInjection(infoString), infoString)
      assertSame(MarkdownLanguage.INSTANCE, CodeFenceLanguageGuesser.guessLanguageWithExtensionForInjection(infoString)?.first, infoString)
      assertSame(language, CodeFenceLanguageGuesser.guessLanguageForExecution(infoString), infoString)
    }
  }
}
