// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.microservices

import com.intellij.microservices.url.references.hasUsageUrlPathReferences
import com.intellij.psi.PsiFile
import com.intellij.xml.psi.codeInsight.XmlAutoPopupEnabler

internal class UrlReferencesXmlAutoPopupEnabler: XmlAutoPopupEnabler {
  override fun shouldShowPopup(file: PsiFile, offset: Int): Boolean {
    val psiElement = file.findElementAt(offset) ?: return false
    return hasUsageUrlPathReferences(psiElement, offset)
  }
}