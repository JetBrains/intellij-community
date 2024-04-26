package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.StandardPatterns.or
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.intellij.util.ProcessingContext
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyCallExpressionHelper
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyLiteralType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeUtil
import com.jetbrains.python.psi.types.TypeEvalContext

class PyLiteralTypeCompletionContributor : CompletionContributor() {
  init {
    extend(
      CompletionType.BASIC,
      or(
        psiElement().withSuperParent(2, PyKeywordArgument::class.java),
        psiElement().withSuperParent(2, PyArgumentList::class.java),
        psiElement().withSuperParent(2, PySubscriptionExpression::class.java),
        psiElement().inside(PyAssignmentStatement::class.java),
      ),
      PyLiteralTypeCompletionProvider()
    )
  }
}

private class PyLiteralTypeCompletionProvider : CompletionProvider<CompletionParameters?>() {
  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val position = parameters.position.parent as? PyExpression ?: return
    val typeEvalContext = TypeEvalContext.codeCompletion(position.project, position.containingFile)

    val callSiteExpr = PsiTreeUtil.getParentOfType(position, PyCallSiteExpression::class.java)
    if (callSiteExpr != null) {
      val parent = position.parent
      val argumentExpr = if (parent is PyKeywordArgument && parent.valueExpression == position) parent else position
      val types = PyCallExpressionHelper
        .mapArguments(callSiteExpr, PyResolveContext.defaultContext(typeEvalContext))
        .mapNotNull { it.mappedParameters[argumentExpr]?.getArgumentType(typeEvalContext) }
      addToResult(position, types, result)
      return
    }

    val assignmentStatement = PsiTreeUtil.skipParentsOfType(position,
                                                            PyParenthesizedExpression::class.java,
                                                            PyTupleExpression::class.java) as? PyAssignmentStatement
    if (assignmentStatement != null) {
      val mapping = assignmentStatement.targetsToValuesMapping.find { it.second === position }
      if (mapping != null) {
        val type = typeEvalContext.getType(mapping.first)
        addToResult(position, listOfNotNull(type), result)
      }
    }
  }

  private fun addToResult(position: PyExpression, possibleTypes: List<PyType>, result: CompletionResultSet) {
    val lookupString = if (position is PyStringLiteralExpression)
      StringLiteralExpression::getStringValue
    else
      PsiElement::getText

    possibleTypes.asSequence()
      .flatMap { PyTypeUtil.toStream(it) }
      .filterIsInstance<PyLiteralType>()
      .map { it.expression }
      .filterIsInstance<PyStringLiteralExpression>()
      .forEach {
        result.addElement(
          PrioritizedLookupElement.withPriority(
            LookupElementBuilder
              .create(lookupString(it))
              .withIcon(IconManager.getInstance().getPlatformIcon(PlatformIcons.Parameter)),
            PythonCompletionWeigher.PRIORITY_WEIGHT.toDouble()
          )
        )
      }
  }
}