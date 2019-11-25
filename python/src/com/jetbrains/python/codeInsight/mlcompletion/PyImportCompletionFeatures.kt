// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.mlcompletion

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiElement
import com.jetbrains.python.codeInsight.completion.hasImportsFrom
import com.jetbrains.python.psi.PyImportElement
import com.jetbrains.python.psi.PyImportStatement
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.resolve.QualifiedNameFinder

object PyImportCompletionFeatures {
  data class ElementImportPathFeatures (val isImported: Boolean,
                                        val numPrivateComponents: Int,
                                        val numComponents: Int)

  fun getElementImportPathFeatures(element: LookupElement, location: CompletionLocation): ElementImportPathFeatures? {
    val psiElement = element.psiElement ?: return null
    val importPath = QualifiedNameFinder.findShortestImportableQName(psiElement.containingFile) ?: return null
    val caretLocationFile = location.completionParameters.originalFile
    val isImported = hasImportsFrom(caretLocationFile, importPath)
    val numComponents = importPath.componentCount
    val numPrivateComponents = importPath.components.count{ it.startsWith("_") }
    return ElementImportPathFeatures(isImported, numPrivateComponents, numComponents)
  }

  fun getImportPopularityFeature(locationPsi: PsiElement, lookupString: String): Int? {
    if (locationPsi.parent !is PyReferenceExpression) return null
    if (locationPsi.parent.parent !is PyImportElement) return null
    if (locationPsi.parent.parent.parent !is PyImportStatement) return null
    return PyMlCompletionHelpers.importPopularity[lookupString]
  }
}