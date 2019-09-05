// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
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

  override fun doInvoke(project: Project, editor: Editor, file: PsiFile) {
    val statement = findStatement(file, editor) ?: return
    val targetPath = statement.importSourceQName ?: return
    val importData = PyRelativeImportData.fromString(targetPath.toString(), file as PyFile) ?: return

    replaceImportStatement(statement, file, importData.locationWithDots)
  }

  override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
    if (file !is PyFile) return false
    val statement = findStatement(file, editor) ?: return false
    if (statement.relativeLevel != 0) return false

    val targetPath = statement.importSourceQName ?: return false
    val filePath = QualifiedNameFinder.findCanonicalImportPath(file, null) ?: return false
    return targetPath.firstComponent == filePath.firstComponent
  }
}