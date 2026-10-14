// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.xml.backend

import com.intellij.codeInsight.completion.HtmlCompletionContributor
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.spellchecker.xml.NoSpellchecking
import com.intellij.spellchecker.xml.XmlSpellcheckingHelper
import com.intellij.util.xml.DomUtil

internal class XmlSpellcheckingHelperImpl : XmlSpellcheckingHelper {
  override fun isSuppressedByDom(element: PsiElement): Boolean {
    return DomUtil.getDomElement(element)?.getAnnotation(NoSpellchecking::class.java) != null
  }

  override fun hasSpecificHtmlCompletions(value: XmlAttributeValue): Boolean {
    val attribute = value.parent as? XmlAttribute ?: return false
    return HtmlCompletionContributor.hasHtmlAttributesCompletion(value) &&
           HtmlCompletionContributor.addSpecificCompletions(attribute).isNotEmpty()
  }
}
