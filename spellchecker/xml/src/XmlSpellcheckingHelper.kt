// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.xml

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlAttributeValue
import org.jetbrains.annotations.ApiStatus

/**
 * Gives the XML and HTML spellchecking strategies the facts that need the XML DOM and the HTML completion.
 *
 * The implementation is in `intellij.spellchecker.xml.backend`.
 * Without it, the strategies check all XML and HTML text.
 */
@ApiStatus.Internal
interface XmlSpellcheckingHelper {
  /**
   * Tells if a DOM annotation, such as [NoSpellchecking], turns off spellchecking for [element].
   */
  fun isSuppressedByDom(element: PsiElement): Boolean

  /**
   * Tells if the HTML completion offers a fixed set of values for the attribute of [value], for example for `target` or `rel`.
   */
  fun hasSpecificHtmlCompletions(value: XmlAttributeValue): Boolean

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<XmlSpellcheckingHelper> = ExtensionPointName("com.intellij.spellchecker.xmlSpellcheckingHelper")
  }
}
