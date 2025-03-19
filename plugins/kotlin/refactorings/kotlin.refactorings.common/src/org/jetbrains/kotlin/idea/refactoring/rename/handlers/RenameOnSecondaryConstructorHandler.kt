// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename.handlers


import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.*


class RenameOnSecondaryConstructorHandler : RenameHandler {
  override fun isAvailableOnDataContext(dataContext: DataContext): Boolean {
    val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return false
    val file = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return false

    val element = PsiTreeUtil.findElementOfClassAtOffsetWithStopSet(
      file,
      editor.caretModel.offset,
      KtSecondaryConstructor::class.java,
      /* strictStart = */ false,
      KtBlockExpression::class.java,
      KtValueArgumentList::class.java,
      KtParameterList::class.java,
      KtConstructorDelegationCall::class.java
    )
    return element != null
  }

  override fun isRenaming(dataContext: DataContext): Boolean = isAvailableOnDataContext(dataContext)

  override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
    CommonRefactoringUtil.showErrorHint(
      project,
      editor,
      KotlinBundle.message("text.rename.is.not.applicable.to.secondary.constructors"),
      RefactoringBundle.message("rename.title"),
      null
    )
  }

  override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
    // Do nothing: this method is called not from editor
  }
}

