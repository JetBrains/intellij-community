// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.embedding

import com.intellij.lang.HtmlScriptContentProvider
import com.intellij.lang.Language
import com.intellij.lang.LanguageHtmlScriptContentProvider
import com.intellij.lang.LanguageUtil
import com.intellij.lexer.BaseHtmlLexer
import com.intellij.lexer.EmbeddedTokenTypesProvider
import com.intellij.lexer.Lexer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.tree.IElementType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.TestOnly
import java.util.stream.Stream

/**
 * @see HtmlScriptContentProvider
 */
interface HtmlEmbeddedContentSupport {
  fun isEnabled(lexer: BaseHtmlLexer): Boolean = true

  fun createEmbeddedContentProviders(lexer: BaseHtmlLexer): List<HtmlEmbeddedContentProvider> = emptyList()

  companion object {
    @JvmField
    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<HtmlEmbeddedContentSupport> = ExtensionPointName("com.intellij.html.embeddedContentSupport")

    fun getContentSupports(): @NotNull Stream<HtmlEmbeddedContentSupport> {
      return EP_NAME.extensionList.stream()
    }

    @JvmStatic
    fun getStyleTagEmbedmentInfo(language: Language): HtmlEmbedmentInfo? {
      return if (LanguageUtil.isInjectableLanguage(language))
        EmbeddedTokenTypesProvider.getProviders()
          .map { it.elementType }
          .filter { language.`is`(it.language) }
          .map { elementType ->
            HtmlLanguageEmbedmentInfo(elementType, language)
          }
          .firstOrNull()
      else null
    }

    @JvmStatic
    fun getScriptTagEmbedmentInfo(language: Language): HtmlEmbedmentInfo? {
      return if (LanguageUtil.isInjectableLanguage(language))
        LanguageHtmlScriptContentProvider.getScriptContentProvider(language)
          ?.let { provider ->
            object: HtmlEmbedmentInfo { // weird debug name
              override fun getElementType(): IElementType?  = provider.scriptElementType
              override fun createHighlightingLexer(): Lexer?  = provider.highlightingLexer
            }
          }
      else null
    }

    /**
     * Use this method to register support in ParsingTestCases only
     */
    @TestOnly
    @JvmStatic
    fun register(application: Application, disposable: Disposable, vararg supports: Class<out HtmlEmbeddedContentSupport>) {
      val name = EP_NAME.name
      val extensionPoint = application.extensionArea.let {
        val point = it.getExtensionPointIfRegistered<HtmlEmbeddedContentSupport>(name)
        if (point == null) {
          it.registerExtensionPoint(name, HtmlEmbeddedContentSupport::class.java.name, ExtensionPoint.Kind.INTERFACE, true)
          it.getExtensionPoint(name)
        }
        else {
          point
        }
      }
      supports.asSequence().map { it.getDeclaredConstructor().newInstance() }
        .forEach { extensionPoint.registerExtension(it, disposable) }
    }
  }
}
