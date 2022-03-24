// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.suggested

import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.suggested.*

internal class PySuggestedRefactoringAvailability(support: PySuggestedRefactoringSupport) : SuggestedRefactoringAvailability(support) {

  override fun shouldSuppressRefactoringForDeclaration(state: SuggestedRefactoringState): Boolean {
    return PySuggestedRefactoringSupport.shouldSuppressRefactoringForDeclaration(state)
  }

  override fun detectAvailableRefactoring(state: SuggestedRefactoringState): SuggestedRefactoringData? {
    val declaration = state.declaration
    return when {
      declaration == null -> null
      PySuggestedRefactoringSupport.isAvailableForChangeSignature(declaration) -> {
        SuggestedChangeSignatureData.create(state, RefactoringBundle.message("suggested.refactoring.usages"))
      }
      PySuggestedRefactoringSupport.isAvailableForRename(declaration) -> {
        SuggestedRenameData(declaration as PsiNameIdentifierOwner, state.oldSignature.name)
      }
      else -> null
    }
  }
}