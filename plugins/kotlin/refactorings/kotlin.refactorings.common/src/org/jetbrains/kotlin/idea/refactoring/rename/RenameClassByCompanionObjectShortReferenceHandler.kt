// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.codeinsight.utils.resolveCompanionObjectShortReferenceToContainingClassSymbol
import org.jetbrains.kotlin.idea.references.mainReference

class RenameClassByCompanionObjectShortReferenceHandler : AbstractReferenceSubstitutionRenameHandler() {
  override fun getElementToRename(dataContext: DataContext): PsiElement? {
    val refExpr = getReferenceExpression(dataContext) ?: return null
    @OptIn(KaAllowAnalysisOnEdt::class)
    allowAnalysisOnEdt {
      analyze(refExpr) {
        return refExpr.mainReference.resolveCompanionObjectShortReferenceToContainingClassSymbol()?.psi
      }
    }
  }
}