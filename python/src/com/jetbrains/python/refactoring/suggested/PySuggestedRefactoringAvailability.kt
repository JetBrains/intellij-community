// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.suggested

import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.suggested.SuggestedChangeSignatureData
import com.intellij.refactoring.suggested.SuggestedRefactoringAvailability
import com.intellij.refactoring.suggested.SuggestedRefactoringData
import com.intellij.refactoring.suggested.SuggestedRefactoringState
import com.intellij.refactoring.suggested.SuggestedRenameData
import com.jetbrains.python.psi.PyImportElement

internal class PySuggestedRefactoringAvailability(support: PySuggestedRefactoringSupport) : SuggestedRefactoringAvailability(support) {

  override fun shouldSuppressRefactoringForDeclaration(state: SuggestedRefactoringState): Boolean {
    return PySuggestedRefactoringSupport.Helper.shouldSuppressRefactoringForDeclaration(state)
  }

  override fun detectAvailableRefactoring(state: SuggestedRefactoringState): SuggestedRefactoringData? {
    val declaration = state.declaration
    return when {
      declaration == null -> null
      PySuggestedRefactoringSupport.Helper.isAvailableForChangeSignature(declaration) -> {
        SuggestedChangeSignatureData.create(state, RefactoringBundle.message("suggested.refactoring.usages"))
      }
      PySuggestedRefactoringSupport.Helper.isAvailableForRename(declaration) -> {
        SuggestedRenameData(declaration as PsiNameIdentifierOwner, state.oldSignature.name)
      }
      PySuggestedRefactoringSupport.Helper.isAvailableForImportAlias(declaration) -> {
        // Only an added/changed alias renames usages; editing the imported module name (no alias) does not.
        val alias = (declaration as PyImportElement).asNameElement
        val oldName = state.oldSignature.name
        if (alias == null || alias.name == oldName) null
        else SuggestedRenameData(alias, oldName)
      }
      else -> null
    }
  }
}