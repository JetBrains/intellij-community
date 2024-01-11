// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.jetbrains.python.extensions.getQName
import com.jetbrains.python.psi.PyFile

/**
 * Converts location of 'from .three import foo' to absolute path, e.g. 'from one.two.three import foo'.
 *
 * @see PyRelativeToAbsoluteImportIntention
 * @author Aleksei.Kniazev
 */
class PyRelativeToAbsoluteImportIntention : PyConvertImportIntentionAction("INTN.convert.relative.to.absolute") {

  override fun doInvoke(project: Project, editor: Editor, file: PsiFile) {
    val statement = findStatement(file, editor) ?: return
    val source = statement.resolveImportSource() ?: return
    val qName = source.getQName()

    replaceImportStatement(statement, file, qName.toString())
  }

  override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
    if (file !is PyFile) return false
    val statement = findStatement(file, editor) ?: return false
    return statement.relativeLevel  > 0
  }
}