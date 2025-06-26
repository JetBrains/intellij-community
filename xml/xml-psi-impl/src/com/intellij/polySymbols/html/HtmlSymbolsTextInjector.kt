// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html

import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolModifier
import com.intellij.polySymbols.query.PolySymbolQueryExecutorFactory
import com.intellij.polySymbols.utils.asSingleSymbol
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.html.HtmlTag
import com.intellij.psi.impl.source.xml.XmlTextImpl
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlText
import com.intellij.util.asSafely
import java.util.*

class HtmlSymbolsTextInjector : MultiHostInjector {

  override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
    val languageToInject =
      (context as? XmlText)
        ?.parent?.asSafely<HtmlTag>()
        ?.let { tag ->
          CachedValuesManager.getCachedValue(tag) {
            val queryExecutor = PolySymbolQueryExecutorFactory.create(tag, false)
            CachedValueProvider.Result.create(
              queryExecutor.nameMatchQuery(HTML_ELEMENTS, tag.name)
                .exclude(PolySymbolModifier.ABSTRACT)
                .run()
                .getLanguageToInject(),
              PsiModificationTracker.MODIFICATION_COUNT, queryExecutor
            )
          }
        }
      ?: (context as? XmlAttributeValue)
        ?.parent?.asSafely<XmlAttribute>()
        ?.takeIf { it.parent is HtmlTag }
        ?.let { attr ->
          CachedValuesManager.getCachedValue(attr) {
            val tag = attr.parent as HtmlTag
            val queryExecutor = PolySymbolQueryExecutorFactory.create(tag, false)
            CachedValueProvider.Result.create(
              queryExecutor.nameMatchQuery(
                listOf(
                  HTML_ELEMENTS.withName(tag.name),
                  HTML_ATTRIBUTES.withName(attr.name))
              )
                .exclude(PolySymbolModifier.ABSTRACT)
                .run()
                .getLanguageToInject(),
              PsiModificationTracker.MODIFICATION_COUNT, queryExecutor
            )
          }
        }
      ?: return
    findLanguages(languageToInject)
      .firstOrNull { LanguageUtil.isInjectableLanguage(it) }
      ?.let {
        registrar.startInjecting(it)
          .addPlace(null, null, context as PsiLanguageInjectionHost,
                    ElementManipulators.getValueTextRange(context))
          .doneInjecting()
      }
  }

  override fun elementsToInjectIn(): List<Class<out PsiElement>> =
    listOf(XmlTextImpl::class.java, XmlAttributeValue::class.java)

  private fun List<PolySymbol>.getLanguageToInject() =
    takeIf { it.isNotEmpty() && !it.hasOnlyStandardHtmlSymbols() }
      ?.asSingleSymbol()
      ?.get(PolySymbol.PROP_INJECT_LANGUAGE)
      ?.lowercase(Locale.US)

  private fun findLanguages(scriptLang: String): Sequence<Language> =
    Language.findInstancesByMimeType(scriptLang)
      .asSequence()
      .plus(Language.findInstancesByMimeType("text/$scriptLang"))
      .plus(
        FileTypeManager.getInstance().getFileTypeByExtension(scriptLang)
          .let { scriptFileType ->
            Language.getRegisteredLanguages()
              .asSequence()
              .filter { lang ->
                scriptLang.equals(lang.id, ignoreCase = true)
                || scriptFileType === lang.associatedFileType
              }
          }
      )

}