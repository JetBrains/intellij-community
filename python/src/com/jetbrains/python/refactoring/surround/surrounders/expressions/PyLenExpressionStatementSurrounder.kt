// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.surround.surrounders.expressions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.util.IncorrectOperationException
import com.jetbrains.python.psi.*


class PyLenExpressionStatementSurrounder : PyExpressionSurrounder() {

  @Throws(IncorrectOperationException::class)
  override fun surroundExpression(project: Project, editor: Editor, element: PyExpression): TextRange {
    val generator = PyElementGenerator.getInstance(project)
    val callExpression = generator.createExpressionFromText(LanguageLevel.forElement(element), "len(${element.text})")
    val replacement = element.replace(callExpression)
    return TextRange.from(replacement.textRange.endOffset, 0)
  }

  override fun isApplicable(expr: PyExpression) = true

  override fun getTemplateDescription()= "len(expr)"
}
