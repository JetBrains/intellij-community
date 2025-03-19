// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis

import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider
import com.intellij.psi.PsiReference
import com.intellij.psi.xml.XmlElement

class XmlUnresolvedReferenceInspection: XmlReferenceInspectionBase() {
  override fun needToCheckRef(reference: PsiReference?) = !XmlHighlightVisitor.shouldCheckResolve(reference) &&
                                                          !XmlHighlightVisitor.isUrlReference(reference)

  override fun checkRanges() = false

  override fun checkHtml(element: XmlElement, isHtml: Boolean) = true
}

/**
 * Marker interface for references associated with [UnresolvedReferenceQuickFixProvider],
 * which should be checked by [XmlHighlightVisitor]
 */
interface PsiReferenceWithUnresolvedQuickFixes: PsiReference