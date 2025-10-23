package com.jetbrains.python.inspections.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyTupleExpression

class WrapExceptTupleInParenthesesQuickFix(val exceptPartTuple: PyTupleExpression) : IntentionAction {

  override fun getFamilyName(): String = PyPsiBundle.message("QFIX.except.clause.missing.parens")

  override fun getText(): @IntentionName String = PyPsiBundle.message("QFIX.except.clause.missing.parens")

  override fun isAvailable(project: Project, editor: Editor?, psiFile: PsiFile?): Boolean = true

  override fun startInWriteAction(): Boolean = true

  override fun invoke(project: Project, editor: Editor?, psiFile: PsiFile?) {
    val generator = PyElementGenerator.getInstance(project)
    val level = LanguageLevel.forElement(exceptPartTuple)
    val wrapped = generator.createExpressionFromText(level, "(${exceptPartTuple.text})")
    exceptPartTuple.replace(wrapped)
  }
}