// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.HtmlCompletionContributor
import com.intellij.polySymbols.html.attributeValues.HtmlAttributeValueSymbolsCompletionProvider
import com.intellij.polySymbols.html.attributes.HtmlAttributeSymbolsCompletionProvider
import com.intellij.polySymbols.html.elements.HtmlElementSymbolsCompletionProvider
import com.intellij.polySymbols.html.elements.HtmlElementNameSymbolsInTextCompletionProvider
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.html.HtmlTag
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlTokenType

class HtmlSymbolsCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC,
           psiElement(XmlTokenType.XML_NAME).withParent(psiElement(XmlAttribute::class.java)
                                                          .withParent(HtmlTag::class.java)),
           HtmlAttributeSymbolsCompletionProvider())

    extend(CompletionType.BASIC,
           psiElement(XmlTokenType.XML_NAME).withParent(HtmlTag::class.java),
           HtmlElementSymbolsCompletionProvider())

    extend(CompletionType.BASIC,
           psiElement(XmlTokenType.XML_TAG_NAME).withParent(HtmlTag::class.java),
           HtmlElementSymbolsCompletionProvider())

    extend(CompletionType.BASIC,
           psiElement().withElementType(TokenSet.create(XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN))
             .withSuperParent(1, XmlAttributeValue::class.java)
             .withSuperParent(2, XmlAttribute::class.java)
             .withSuperParent(3, HtmlTag::class.java),
           HtmlAttributeValueSymbolsCompletionProvider())

    extend(CompletionType.BASIC,
           HtmlCompletionContributor.getHtmlElementInTextPattern(),
           HtmlElementNameSymbolsInTextCompletionProvider())
  }
}