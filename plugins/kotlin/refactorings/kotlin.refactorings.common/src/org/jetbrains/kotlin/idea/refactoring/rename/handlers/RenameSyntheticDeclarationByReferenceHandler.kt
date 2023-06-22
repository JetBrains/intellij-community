// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename.handlers

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbolOrigin
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.rename.findElementForRename
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

class RenameSyntheticDeclarationByReferenceHandler : RenameHandler {
  override fun isAvailableOnDataContext(dataContext: DataContext): Boolean {
    val file = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return false
    val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return false
    val refExpression = file.findElementForRename<KtSimpleNameExpression>(editor.caretModel.offset) ?: return false
    @OptIn(KtAllowAnalysisOnEdt::class)
    allowAnalysisOnEdt {
      analyze(refExpression) {
        val target = refExpression.mainReference.resolveToSymbol() ?: return false
        return target.origin == KtSymbolOrigin.SOURCE_MEMBER_GENERATED
      }
    }
  }

  override fun isRenaming(dataContext: DataContext) = isAvailableOnDataContext(dataContext)

  override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
    CommonRefactoringUtil.showErrorHint(
      project,
      editor,
      KotlinBundle.message("text.rename.is.not.applicable.to.synthetic.declarations"),
      RefactoringBundle.message("rename.title"),
      null
    )
  }

  override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
    // Do nothing: this method is not called from editor
  }
}
