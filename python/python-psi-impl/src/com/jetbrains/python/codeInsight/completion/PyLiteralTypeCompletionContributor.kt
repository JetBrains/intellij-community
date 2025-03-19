package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.ml.MLRankingIgnorable
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.intellij.util.ProcessingContext
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyCallExpressionHelper
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyLiteralType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeUtil
import com.jetbrains.python.psi.types.TypeEvalContext

class PyLiteralTypeCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC, psiElement(), PyLiteralTypeCompletionProvider())
  }
}

private class PyLiteralTypeCompletionProvider : CompletionProvider<CompletionParameters?>() {
  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val position = parameters.position.parent as? PyExpression ?: return
    val typeEvalContext = TypeEvalContext.codeCompletion(position.project, position.containingFile)

    val mappedParameters = PyCallExpressionHelper.getMappedParameters(position, PyResolveContext.defaultContext(typeEvalContext))
    if (mappedParameters != null) {
      val types = mappedParameters.mapNotNull { it.getArgumentType(typeEvalContext) }
      addToResult(position, types, result)
      return
    }

    val assignmentStatement = PsiTreeUtil.skipParentsOfType(position,
                                                            PyParenthesizedExpression::class.java,
                                                            PyTupleExpression::class.java) as? PyAssignmentStatement
    if (assignmentStatement != null) {
      val mapping = assignmentStatement.targetsToValuesMapping.find { PyPsiUtils.flattenParens(it.second) === position }
      if (mapping != null) {
        val type = typeEvalContext.getType(mapping.first)
        addToResult(position, listOfNotNull(type), result)
      }
      return
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
          MLRankingIgnorable.wrap(
            PrioritizedLookupElement.withPriority(
              LookupElementBuilder
                .create(lookupString(it))
                .withIcon(IconManager.getInstance().getPlatformIcon(PlatformIcons.Parameter)),
              PythonCompletionWeigher.PRIORITY_WEIGHT.toDouble()
            )
          )
        )
      }
  }
}