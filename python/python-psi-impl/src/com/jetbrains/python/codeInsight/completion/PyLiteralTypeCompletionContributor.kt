package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.StandardPatterns.or
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.intellij.util.ProcessingContext
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyCallExpressionHelper
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyLiteralType
import com.jetbrains.python.psi.types.PyTypeUtil
import com.jetbrains.python.psi.types.TypeEvalContext

class PyLiteralTypeCompletionContributor : CompletionContributor() {
  init {
    extend(
      CompletionType.BASIC,
      or(
        psiElement().withSuperParent(2, PyKeywordArgument::class.java),
        psiElement().withSuperParent(2, PyArgumentList::class.java),
        psiElement().withSuperParent(2, PySubscriptionExpression::class.java)
      ),
      PyLiteralTypeCompletionProvider()
    )
  }
}

private class PyLiteralTypeCompletionProvider : CompletionProvider<CompletionParameters?>() {
  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val position = parameters.position.parent as? PyExpression ?: return
    val callSiteExpr = PsiTreeUtil.getParentOfType(position, PyCallSiteExpression::class.java) ?: return

    val parent = position.parent
    val argumentExpr = if (parent is PyKeywordArgument && parent.valueExpression == position) parent else position
    val typeEvalContext = TypeEvalContext.codeCompletion(position.project, position.containingFile)
    val types = PyCallExpressionHelper.mapArguments(callSiteExpr, PyResolveContext.defaultContext(typeEvalContext))
      .mapNotNull { it.mappedParameters[argumentExpr]?.getArgumentType(typeEvalContext) }
      .flatMap { PyTypeUtil.toStream(it) }
      .filterIsInstance<PyLiteralType>()

    for (type in types) {
      val expression = type.expression
      if (position is PyStringLiteralExpression) {
        if (expression is PyStringLiteralExpression) {
          addToResult(result, expression.stringValue)
        }
      }
      else if (expression is PyStringLiteralExpression || expression is PyNumericLiteralExpression) {
        addToResult(result, expression.text)
      }
    }
  }

  private fun addToResult(result: CompletionResultSet, lookupString: String) {
    result.addElement(
      LookupElementBuilder
        .create(lookupString)
        .withIcon(IconManager.getInstance().getPlatformIcon(PlatformIcons.Parameter))
    )
  }
}