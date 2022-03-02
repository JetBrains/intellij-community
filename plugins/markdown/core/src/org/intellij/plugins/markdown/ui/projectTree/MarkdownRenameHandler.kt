// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.projectTree

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandlerFactory
import com.intellij.refactoring.rename.RenameHandler
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile

class MarkdownRenameHandler : RenameHandler {
  override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) {
    RefactoringActionHandlerFactory.getInstance().createRenameHandler().invoke(project, editor, file, dataContext)
  }

  override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
    val mdFile = elements.find { it.containingFile.virtualFile.extension == MarkdownFileType.INSTANCE.defaultExtension } ?: return
    RefactoringActionHandlerFactory.getInstance().createRenameHandler().invoke(project, arrayOf(mdFile), dataContext)
  }

  override fun isAvailableOnDataContext(dataContext: DataContext): Boolean {
    val mdFiles = CommonDataKeys.PSI_FILE.getData(dataContext)
    return mdFiles is MarkdownFile
  }
}
