// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.psi.PsiElement
import com.jetbrains.python.codeInsight.imports.PyRelativeImportData
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.resolve.QualifiedNameFinder

/**
 * Converts location of 'from one.two.three import foo' to the one relative to the current file, e.g. 'from .three import foo'.
 *
 * @see PyRelativeToAbsoluteImportIntention
 * @author Aleksei.Kniazev
 */
class PyAbsoluteToRelativeImportIntention : PyConvertImportIntentionAction("INTN.convert.absolute.to.relative") {
  override fun getPresentation(context: ActionContext, element: PsiElement): Presentation? {
    if (context.file !is PyFile) return null
    val statement = findStatement(element) ?: return null
    if (statement.relativeLevel != 0) return null

    val targetPath = statement.importSourceQName ?: return null
    val filePath = QualifiedNameFinder.findCanonicalImportPath(context.file, null) ?: return null
    return if(targetPath.firstComponent == filePath.firstComponent) super.getPresentation(context, element) else null
  }

  override fun invoke(context: ActionContext, element: PsiElement, updater: ModPsiUpdater) {
    val statement = findStatement(element) ?: return
    val targetPath = statement.importSourceQName ?: return
    val importData = PyRelativeImportData.fromString(targetPath.toString(), context.file as PyFile) ?: return

    replaceImportStatement(statement, context.file, importData.locationWithDots)
  }
}