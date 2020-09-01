// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.common

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.getPossiblePsiSourceTypes
import org.jetbrains.uast.toUElementOfExpectedTypes
import org.junit.Assert


interface PossibleSourceTypesTestBase {

  private fun PsiFile.getPsiSourcesByPlainVisitor(psiPredicate: (PsiElement) -> Boolean = { true },
                                                  vararg uastTypes: Class<out UElement>): Set<PsiElement> {
    val sources = mutableSetOf<PsiElement>()
    accept(object : PsiRecursiveElementVisitor() {
      override fun visitElement(element: PsiElement) {
        if (psiPredicate(element))
          element.toUElementOfExpectedTypes(*uastTypes)?.let { sources += element }
        super.visitElement(element)
      }
    })
    return sources
  }

  private fun PsiFile.getPsiSourcesByLanguageAwareVisitor(vararg uastTypes: Class<out UElement>): Set<PsiElement> {
    val possibleSourceTypes = getPossiblePsiSourceTypes(language, *uastTypes)
    return getPsiSourcesByPlainVisitor(psiPredicate = { it.javaClass in possibleSourceTypes }, uastTypes = *uastTypes)
  }

  private fun PsiFile.getPsiSourcesByLanguageUnawareVisitor(vararg uastTypes: Class<out UElement>): Set<PsiElement> {
    val possibleSourceTypes = UastFacade.getPossiblePsiSourceTypes(*uastTypes)
    return getPsiSourcesByPlainVisitor(psiPredicate = { it.javaClass in possibleSourceTypes }, uastTypes = *uastTypes)
  }

  fun checkConsistencyWithRequiredTypes(psiFile: PsiFile, vararg uastTypes: Class<out UElement>) {
    val byPlain = psiFile.getPsiSourcesByPlainVisitor(uastTypes = *uastTypes)
    val byLanguageAware = psiFile.getPsiSourcesByLanguageAwareVisitor(uastTypes = *uastTypes)
    val byLanguageUnaware = psiFile.getPsiSourcesByLanguageUnawareVisitor(uastTypes = *uastTypes)

    Assert.assertEquals(
      "Filtering PSI elements with getPossiblePsiSourceTypes should not lost or add any conversions",
      byPlain,
      byLanguageUnaware)

    Assert.assertEquals(
      "UastFacade implementation should be in sync with language UastLanguagePlugin's one",
      byLanguageAware,
      byLanguageUnaware)
  }

}