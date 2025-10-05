// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PyPandasTruthValueIsAmbiguousInspection : PyInspection() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return Visitor(holder, PyInspectionVisitor.getContext(session))
  }

  private class Visitor(private val holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {
    override fun visitPyAssertStatement(node: PyAssertStatement) {
      super.visitPyAssertStatement(node)
      val expression = node.getArguments().firstOrNull() ?: return
      reportProblems(expression)
    }

    override fun visitPyConditionalStatementPart(node: PyConditionalStatementPart) {
      super.visitPyConditionalStatementPart(node)
      val condition = node.condition ?: return
      reportProblems(condition)
    }

    private fun reportProblems(expression: PyExpression) {
      if (expression is PyBinaryExpression) {
        expression.accept(BinaryExpressionRecursiveVisitor(holder, myTypeEvalContext))
      }
      else {
        expression.reportProblemIfPandasAmbiguousTarget(holder, myTypeEvalContext)
      }
    }
  }

  private class BinaryExpressionRecursiveVisitor(private val holder: ProblemsHolder, private val context: TypeEvalContext) : PyRecursiveElementVisitor() {
    override fun visitPyBinaryExpression(node: PyBinaryExpression) {
      val isIdentity = node.isOperator(PyNames.IS) || node.isOperator("isnot")
      if (!isIdentity) {
        val suggestedQuickFixes = node.isOperator(PyNames.AND) || node.isOperator(PyNames.OR) || node.isOperator("==")
        node.leftExpression?.reportProblemIfPandasAmbiguousTarget(holder, context, suggestedQuickFixes)
        node.rightExpression?.reportProblemIfPandasAmbiguousTarget(holder, context, suggestedQuickFixes)
      }
      super.visitPyBinaryExpression(node)
    }
  }
}

private fun PyExpression?.reportProblemIfPandasAmbiguousTarget(holder: ProblemsHolder, typeEvalContext: TypeEvalContext, suggestQuickFixes: Boolean = true) {
  this ?: return
  val pandasAmbiguousTarget = when (this) {
    is PyPrefixExpression -> this.operand.resolvePandasAmbiguousTarget(typeEvalContext)
    is PyReferenceExpression -> this.resolvePandasAmbiguousTarget(typeEvalContext)
    else -> null
  } ?: return
  val quickFixes = when {
    !suggestQuickFixes -> emptyArray()
    this is PyPrefixExpression -> arrayOf(NotPrefixExpressionReplaceWithEmptyCheckQuickFix())
    this is PyReferenceExpression -> arrayOf(
      ReferenceExpressionReplaceWithEmptyCheckQuickFix(),
      ReferenceExpressionReplaceWithExplicitNotNoneCheckQuickFix()
    )
    else -> emptyArray()
  }
  holder.registerProblem(this, PyPsiBundle.message(pandasAmbiguousTarget.inspectionBundleName),
                         *quickFixes)
}

private enum class PandasAmbiguousTarget(
  val qualifiedName: String,
  val inspectionBundleName: String,
) {
  DATA_FRAME(
    qualifiedName = "pandas.core.frame.DataFrame",
    inspectionBundleName = "INSP.pandas.truth.value.is.ambiguous.df"
  ),
  SERIES(
    qualifiedName = "pandas.core.series.Series",
    inspectionBundleName = "INSP.pandas.truth.value.is.ambiguous.series"
  )
}

private fun PyExpression?.resolvePandasAmbiguousTarget(typeEvalContext: TypeEvalContext): PandasAmbiguousTarget? {
  this ?: return null
  val type = typeEvalContext.getType(this)
  val typesToCheck = if (type is PyUnionType) {
    type.members
  }
  else {
    listOf(type)
  }
  return typesToCheck.firstNotNullOfOrNull { memberType ->
    val qualifiedName = memberType?.declarationElement?.qualifiedName
    PandasAmbiguousTarget.entries.find { it.qualifiedName == qualifiedName }
  }
}

private class ReferenceExpressionReplaceWithEmptyCheckQuickFix() : LocalQuickFix {
  override fun getFamilyName(): String = PyPsiBundle.message("QFIX.pandas.truth.value.is.ambiguous.emptiness.check")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement as? PyReferenceExpression ?: return
    val factory = PyElementGenerator.getInstance(project)
    val newElement = factory.createExpressionFromText(LanguageLevel.forElement(element), "not ${element.text}.empty")
    element.replace(newElement)
  }
}

private class ReferenceExpressionReplaceWithExplicitNotNoneCheckQuickFix() : LocalQuickFix {
  override fun getFamilyName(): String = PyPsiBundle.message("QFIX.pandas.truth.value.is.ambiguous.none.check")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement as? PyReferenceExpression ?: return
    val factory = PyElementGenerator.getInstance(project)
    val newElement = factory.createExpressionFromText(LanguageLevel.forElement(element), "${element.text} is not None")
    element.replace(newElement)
  }
}

private class NotPrefixExpressionReplaceWithEmptyCheckQuickFix() : LocalQuickFix {
  override fun getFamilyName(): String = PyPsiBundle.message("QFIX.pandas.truth.value.is.ambiguous.emptiness.check")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement as? PyPrefixExpression ?: return
    val operand = element.operand ?: return
    val factory = PyElementGenerator.getInstance(project)
    val newElement = factory.createExpressionFromText(LanguageLevel.forElement(element), "${operand.text}.empty")
    element.replace(newElement)
  }
}
