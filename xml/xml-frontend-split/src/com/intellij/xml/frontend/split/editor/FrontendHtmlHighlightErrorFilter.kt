// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.frontend.split.editor

import com.intellij.codeInsight.highlighting.HighlightErrorFilter
import com.intellij.idea.AppModeAssertions
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.impl.source.xml.XmlFileImpl

class FrontendHtmlHighlightErrorFilter: HighlightErrorFilter() {
  override fun shouldHighlightErrorElement(element: PsiErrorElement): Boolean {
    val psiFile = element.getContainingFile()

    if (psiFile is XmlFileImpl) return !AppModeAssertions.isFrontend()

    return true
  }
}