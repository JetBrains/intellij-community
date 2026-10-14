// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.jewel

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.InjectableLanguage
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.intellij.plugins.markdown.injection.CodeFenceLanguageProvider
import org.intellij.plugins.markdown.injection.aliases.AdditionalFenceLanguageSuggester
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

@TestApplication
@OptIn(InternalJewelApi::class)
internal class MarkdownCodeFenceLanguageResolverTest {
  private val resolver = MarkdownCodeFenceLanguageResolver()

  @Test
  fun `resolves an alias with the default provider`() {
    for (infoString in listOf("md", "Markdown", " \tmd title=demo ")) {
      val resolved = resolver.resolve(infoString)
      assertSame(MarkdownLanguage.INSTANCE, resolved?.language, infoString)
      assertNull(resolved?.extension, infoString)
    }
  }

  @Test
  fun `forwards the language and the extension from a provider`(@TestDisposable disposable: Disposable) {
    val language = registerInjectableLanguage("MarkdownJewelProviderTest", disposable)
    val received = mutableListOf<String>()
    maskProviders(disposable, provider(language, "htm") { infoString ->
      received += infoString
      infoString == "My-Fence title=x"
    })

    val resolved = resolver.resolve("My-Fence title=x")

    assertSame(language, resolved?.language)
    assertEquals("htm", resolved?.extension)
    assertEquals(listOf("My-Fence title=x"), received)
  }

  @Test
  fun `skips a provider language that is not injectable`(@TestDisposable disposable: Disposable) {
    val notInjectable = object : Language("MarkdownJewelNotInjectableTest", false) {}
    val injectable = registerInjectableLanguage("MarkdownJewelInjectableTest", disposable)
    maskProviders(disposable, provider(notInjectable, "txt") { true }, provider(injectable, null) { true })

    val resolved = resolver.resolve("my-fence")

    assertSame(injectable, resolved?.language)
    assertNull(resolved?.extension)
  }

  @Test
  fun `uses the additional fence language suggesters`(@TestDisposable disposable: Disposable) {
    val language = registerInjectableLanguage("MarkdownJewelSuggesterTest", disposable)
    val suggester = object : AdditionalFenceLanguageSuggester {
      override fun suggestLanguage(name: String): Language? = language.takeIf { name == "my-suggested-fence" }
    }
    ExtensionTestUtil.maskExtensions(SUGGESTER_EP_NAME, listOf(suggester), disposable)

    assertSame(language, resolver.resolve("My-Suggested-Fence")?.language)
  }

  @Test
  fun `returns null when no provider knows the language`(@TestDisposable disposable: Disposable) {
    maskProviders(disposable)

    assertNull(resolver.resolve("md"))
  }

  private fun maskProviders(disposable: Disposable, vararg providers: CodeFenceLanguageProvider) {
    ExtensionTestUtil.maskExtensions(CodeFenceLanguageProvider.EP_NAME, providers.toList(), disposable)
  }

  private fun provider(language: Language, extension: String?, accepts: (String) -> Boolean) =
    object : CodeFenceLanguageProvider {
      override fun getLanguageByInfoString(infoString: String): Language? = language.takeIf { accepts(infoString) }

      override fun getExtensionByInfoString(infoString: String): String? = extension

      override fun getCompletionVariantsForInfoString(parameters: CompletionParameters): List<LookupElement> = emptyList()
    }

  private fun registerInjectableLanguage(id: String, disposable: Disposable): Language {
    val language = object : Language(id), InjectableLanguage {}
    Disposer.register(disposable) {
      language.unregisterLanguage(DefaultPluginDescriptor("com.intellij.markdown.jewel.tests"))
    }
    return language
  }

  private companion object {
    val SUGGESTER_EP_NAME: ExtensionPointName<AdditionalFenceLanguageSuggester> =
      ExtensionPointName.create("org.intellij.markdown.additionalFenceLanguageSuggester")
  }
}
