// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.jewel

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.flow.first
import org.intellij.plugins.markdown.injection.CodeFenceLanguageProvider
import org.jetbrains.jewel.bridge.code.highlighting.CodeHighlighterFactory
import org.jetbrains.jewel.bridge.code.highlighting.codefence.CodeFenceLanguageResolver
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.plugins.textmate.TextMateLanguage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

@TestApplication
@OptIn(InternalJewelApi::class)
internal class MarkdownCodeFenceHighlightingTest {
  private val project by projectFixture()

  @BeforeEach
  fun registerResolver(@TestDisposable disposable: Disposable) {
    // The test application does not load the module descriptors of the Compose plugin and of this module.
    val area = ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl
    if (!area.hasExtensionPoint(RESOLVER_EP_NAME)) {
      area.registerExtensionPoint(RESOLVER_EP_NAME, CodeFenceLanguageResolver::class.java.name, ExtensionPoint.Kind.INTERFACE, disposable)
    }
    ExtensionTestUtil.maskExtensions(RESOLVER_EP_NAME, listOf(MarkdownCodeFenceLanguageResolver()), disposable)
  }

  @Test
  fun `the module descriptors declare the extension point and the resolver`() {
    val extensionPoint = readDescriptor(CodeFenceLanguageResolver::class.java, "intellij.platform.jewel.ideLafBridge.xml")
      .elements("extensionPoint")
      .singleOrNull { it.getAttribute("qualifiedName") == RESOLVER_EP_NAME.name }
    assertNotNull(extensionPoint, "The bridge descriptor must declare ${RESOLVER_EP_NAME.name}")
    assertEquals(CodeFenceLanguageResolver::class.java.name, extensionPoint!!.getAttribute("interface"))

    val resolvers = readDescriptor(MarkdownCodeFenceLanguageResolver::class.java, "intellij.markdown.jewel.xml")
      .elements("codeFenceLanguageResolver")
      .filter { (it.parentNode as Element).getAttribute("defaultExtensionNs") == RESOLVER_EP_NAMESPACE }
      .map { it.getAttribute("implementation") }
    assertEquals(listOf(MarkdownCodeFenceLanguageResolver::class.java.name), resolvers)
  }

  @Test
  fun `resolves the extension of a TextMate bundle`() {
    // The test classpath has no PHP plugin, so the TextMate provider of the Markdown plugin handles the info string.
    val resolved = MarkdownCodeFenceLanguageResolver().resolve("php")

    assertSame(TextMateLanguage.LANGUAGE, resolved?.language)
    assertEquals("php", resolved?.extension)
  }

  @Test
  fun `highlights a code fence with the language and the extension from a provider`(@TestDisposable disposable: Disposable) {
    // The alias table of Jewel does not know "my-fence". Only the Markdown provider gives the TextMate language and the extension.
    val provider = object : CodeFenceLanguageProvider {
      override fun getLanguageByInfoString(infoString: String): Language? = TextMateLanguage.LANGUAGE.takeIf { infoString == "my-fence" }

      override fun getExtensionByInfoString(infoString: String): String? = "php".takeIf { infoString == "my-fence" }

      override fun getCompletionVariantsForInfoString(parameters: CompletionParameters): List<LookupElement> = emptyList()
    }
    ExtensionTestUtil.maskExtensions(CodeFenceLanguageProvider.EP_NAME, listOf(provider), disposable)
    val highlighter = CodeHighlighterFactory.getInstance(project).createHighlighter()

    val highlighted = timeoutRunBlocking { highlighter.highlight("<?php echo \"Hello\"; ?>", "my-fence").first() }

    assertTrue(highlighted.spanStyles.size > 1, highlighted.spanStyles.toString())
  }

  private fun readDescriptor(owner: Class<*>, path: String): Element {
    val stream = checkNotNull(owner.classLoader.getResourceAsStream(path)) { "Missing descriptor $path" }
    return stream.use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it).documentElement }
  }

  private fun Element.elements(tagName: String): List<Element> {
    val nodes = getElementsByTagName(tagName)
    return (0 until nodes.length).map { nodes.item(it) as Element }
  }

  private companion object {
    const val RESOLVER_EP_NAMESPACE = "org.jetbrains.jewel.bridge"

    val RESOLVER_EP_NAME: ExtensionPointName<CodeFenceLanguageResolver> =
      ExtensionPointName.create("$RESOLVER_EP_NAMESPACE.codeFenceLanguageResolver")
  }
}
