// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.suggested

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.refactoring.suggested.*
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.pyi.PyiUtil

class PySuggestedRefactoringSupport : SuggestedRefactoringSupport {

  override fun isDeclaration(psiElement: PsiElement): Boolean {
    return psiElement is PsiNameIdentifierOwner &&
           !PyiUtil.isOverload(psiElement, TypeEvalContext.codeAnalysis(psiElement.project, psiElement.containingFile))
  }

  override fun signatureRange(declaration: PsiElement): TextRange? = nameRange(declaration)

  override fun importsRange(psiFile: PsiFile): TextRange? = null

  override fun nameRange(declaration: PsiElement): TextRange? = (declaration as? PsiNameIdentifierOwner)?.nameIdentifier?.textRange

  override fun isIdentifierStart(c: Char): Boolean = c == '_' || Character.isLetter(c)

  override fun isIdentifierPart(c: Char): Boolean = isIdentifierStart(c) || Character.isDigit(c)

  override val stateChanges: SuggestedRefactoringStateChanges
    get() = SuggestedRefactoringStateChanges.RenameOnly(this)

  override val availability: SuggestedRefactoringAvailability
    get() = SuggestedRefactoringAvailability.RenameOnly(this)

  override val ui: SuggestedRefactoringUI
    get() = SuggestedRefactoringUI.RenameOnly

  override val execution: SuggestedRefactoringExecution
    get() = SuggestedRefactoringExecution.RenameOnly(this)
}