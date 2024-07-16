// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename.handlers

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KaBackingFieldSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinVariableInplaceRenameHandler
import org.jetbrains.kotlin.idea.refactoring.rename.findElementForRename
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

class RenameBackingFieldReferenceHandler : KotlinVariableInplaceRenameHandler() {
  override fun isAvailable(element: PsiElement?, editor: Editor, file: PsiFile): Boolean {
    val refExpression = file.findElementForRename<KtSimpleNameExpression>(editor.caretModel.offset) ?: return false
    if (refExpression.text != "field") return false
    @OptIn(KaAllowAnalysisOnEdt::class)
    allowAnalysisOnEdt {
      analyze(refExpression) {
        val target = refExpression.mainReference.resolveToSymbol() ?: return false
        return target is KaBackingFieldSymbol
      }
    }
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext) {
    editor?.let {
      CommonRefactoringUtil.showErrorHint(
        project,
        editor,
        KotlinBundle.message("text.rename.not.applicable.to.backing.field.reference"),
        RefactoringBundle.message("rename.title"),
        null
      )
    }
  }

  override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext) {
    // Do nothing: this method is called not from editor
  }
}
