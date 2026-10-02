// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.util.parentOfType
import com.jetbrains.python.PyNames
import com.jetbrains.python.inspections.PyRemoveElementFix
import com.jetbrains.python.inspections.quickfix.AddFieldQuickFix
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PsiReferenceEx
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus

/**
 * Reference for names listed in __match_args__ tuples.
 *
 * Similar to [PyDunderSlotsReference], but resolves using [PyType.resolveMember]
 * to account for properties, descriptors, class members, etc.
 */
@ApiStatus.Internal
class PyDunderMatchArgsReference(element: PyStringLiteralExpression) :
  PsiReferenceBase<PyStringLiteralExpression>(element, element.getStringValueTextRanges().firstOrNull()),
  PsiReferenceEx {

  override fun resolve(): PsiElement? {
    val referenceClass = myElement?.parentOfType<PyClass>() ?: return null
    val typeContext = TypeEvalContext.codeAnalysis(myElement.project, myElement.containingFile)

    return referenceClass.getType(typeContext)
      ?.toInstance()
      ?.resolveMember(myElement.stringValue, null, AccessDirection.READ, PyResolveContext.defaultContext(typeContext))
      ?.firstOrNull()
      ?.element
  }

  override fun getUnresolvedHighlightSeverity(context: TypeEvalContext?): HighlightSeverity = HighlightSeverity.WARNING

  override fun getUnresolvedDescription(): String? = null

  override fun getQuickFixes(context: TypeEvalContext): List<LocalQuickFix> {
    val clazz = myElement?.parentOfType<PyClass>() ?: return emptyList()
    return listOf(
      AddFieldQuickFix(myElement.stringValue, PyNames.NONE, clazz.name, true),
      LocalQuickFix.from(PyRemoveElementFix(myElement))!!
    )
  }
}
