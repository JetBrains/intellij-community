// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.surround.surrounders.expressions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.util.IncorrectOperationException
import com.jetbrains.python.psi.*


class PyLenExpressionStatementSurrounder : PyExpressionSurrounder() {

  @Throws(IncorrectOperationException::class)
  override fun surroundExpression(project: Project, editor: Editor, element: PyExpression): TextRange {
    val range = element.textRange
    val statement = PyElementGenerator.getInstance(project).createFromText(LanguageLevel.getDefault(), PyExpressionStatement::class.java, "len(a)")
    val callExpression = statement.expression as PyCallExpression
    val arg = callExpression.arguments[0]
    arg.replace(element)
    element.replace(callExpression)
    return TextRange.from(range.endOffset + FUNCTION_LENGTH, 0)
  }

  override fun isApplicable(expr: PyExpression) = true

  @NlsSafe
  override fun getTemplateDescription() = "len(expr)"

  companion object {
    const val FUNCTION_LENGTH = "len()".length
  }
}
