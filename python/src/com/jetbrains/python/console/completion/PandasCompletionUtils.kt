package com.jetbrains.python.console.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.completion.ml.MLRankingIgnorable
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.lang.LanguageNamesValidation
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl

/**
 *
 * This data class collect information about possible DataFrame.
 * @param psiName - name of PsiElement, that could be DataFrame type
 * @param needValidatorCheck - boolean flag to switch on check for validating columns name
 * @param columnsBefore - list of sub-columns before the place where completion was called
 * LanguageNamesValidation.INSTANCE.forLanguage(PythonLanguage.getInstance()).isIdentifier()
 *
 */
data class PandasDataFrameCandidate(val psiName: String, val needValidatorCheck: Boolean, val columnsBefore: List<String>)


// Priority value to control order in CompletionResultSet
private const val DATAFRAME_COLUMN_PRIORITY = 100.0

fun getCompleteAttribute(parameters: CompletionParameters): List<PandasDataFrameCandidate> {

  val callInnerReferenceExpression = getCallInnerReferenceExpression(parameters)
  val (currentElement, needValidatorCheck) =
    findCompleteAttribute(parameters)
    ?: return getPossibleObjectsDataFrame(parameters, callInnerReferenceExpression)

  return when (currentElement) {
    is PyCallExpression, is PyParenthesizedExpression -> {
      emptyList()
    }
    is PyExpression -> buildList {
      val parentLambdaExpression = collectParentOfLambdaExpression(currentElement, callInnerReferenceExpression)
      parentLambdaExpression?.let {
        add(createPandasDataFrameCandidate(it, needValidatorCheck))
      }
      add(createPandasDataFrameCandidate(currentElement, needValidatorCheck))
    }
    else -> {
      emptyList()
    }
  }
}

private fun createPandasDataFrameCandidate(psiElement: PsiElement, needValidatorCheck: Boolean): PandasDataFrameCandidate {
  val columns = mutableListOf<String>()

  val firstChild: PsiElement = PsiTreeUtil.getDeepestFirst(psiElement)

  val lastChild = PsiTreeUtil.getDeepestLast(psiElement)
  if (firstChild != lastChild) {
    var child = PsiTreeUtil.nextLeaf(firstChild)
    while (child != null) {
      if (child.elementType != PyTokenTypes.DOT && child.elementType != PyTokenTypes.LBRACKET && child.elementType != PyTokenTypes.RBRACKET) {
        when (child) {
          is PyStringLiteralExpression -> {
            columns.add((child as PyStringLiteralExpressionImpl).stringValue)
          }
          is PyPlainStringElement -> {
            columns.add(child.content)
          }
          else -> {
            columns.add(child.text)
          }
        }
      }
      if (child == lastChild) {
        break
      }
      child = PsiTreeUtil.nextLeaf(child)
    }
  }

  return PandasDataFrameCandidate(firstChild.text, needValidatorCheck, columns)
}

private fun getPossibleObjectsDataFrame(parameters: CompletionParameters,
                                        callInnerReferenceExpression: PyExpression?): List<PandasDataFrameCandidate> {
  return setOfNotNull(callInnerReferenceExpression?.text, getSliceSubscriptionReferenceExpression(parameters)?.text,
                      getAttributeReferenceExpression(parameters)?.text).map {

    PandasDataFrameCandidate(it, false, emptyList())
  }
}

private fun findCompleteAttribute(parameters: CompletionParameters): Pair<PsiElement, Boolean>? {
  var needCheck = true
  var element = parameters.position

  if (element.prevSibling?.elementType != PyTokenTypes.DOT && element.parent?.prevSibling?.elementType == PyTokenTypes.LBRACKET) {
    needCheck = false
    element = element.parent
  }

  val exactElement = element.prevSibling?.prevSibling
  when {
    exactElement != null -> return Pair(exactElement, needCheck)

    else -> return null
  }
}

private fun getCallInnerReferenceExpression(parameters: CompletionParameters): PyExpression? {
  var callExpression: PyCallExpression? = PsiTreeUtil.getParentOfType(parameters.position, PyCallExpression::class.java) ?: return null
  var referenceExpression: PyReferenceExpression? = null

  while (true) {
    val tmp = PsiTreeUtil.findChildOfType(callExpression, PyReferenceExpression::class.java)
    if (tmp == null) break
    referenceExpression = tmp
    callExpression = PsiTreeUtil.getChildOfType(referenceExpression, PyCallExpression::class.java)
  }
  if (referenceExpression != null) {
    return PyPsiUtils.getFirstQualifier(referenceExpression)
  }
  return null
}

private fun getSliceSubscriptionReferenceExpression(parameters: CompletionParameters): PyExpression? {
  val expression = PsiTreeUtil.getParentOfType(parameters.position, PySubscriptionExpression::class.java) ?: PsiTreeUtil.getParentOfType(
    parameters.position, PySliceExpression::class.java)
  val result = PsiTreeUtil.getChildOfType(expression, PyReferenceExpression::class.java)
  if (result != null) {
    return PyPsiUtils.getFirstQualifier(result)
  }
  if (expression != null) {
    return getSliceSubscriptionReferenceExpressionWithMultiIndex(expression)
  }
  return null
}

private fun getSliceSubscriptionReferenceExpressionWithMultiIndex(expression: PyExpression): PyExpression? {
  var count = 0
  var child = expression.copy()

  while (PsiTreeUtil.getChildOfType(child, PyReferenceExpression::class.java) == null && child != null) {
    count++
    child = PsiTreeUtil.getChildOfType(child, PySubscriptionExpression::class.java)
  }

  val result = PsiTreeUtil.getChildOfType(child, PyReferenceExpression::class.java)
  if (result != null) {
    return PyPsiUtils.getFirstQualifier(result)
  }
  return null
}

private fun getAttributeReferenceExpression(parameters: CompletionParameters): PyExpression? {
  val result = PsiTreeUtil.getParentOfType(parameters.position, PyReferenceExpression::class.java)

  if (result != null) {
    val child = PsiTreeUtil.getChildrenOfType(result, PyExpression::class.java) ?: emptyArray()
    if (!child.isEmpty()) {
      return child[0]
    }
    return PyPsiUtils.getFirstQualifier(result)
  }

  return null
}

private fun collectParentOfLambdaExpression(element: PyExpression, callInnerReferenceExpression: PyExpression?): PyExpression? {
  if (callInnerReferenceExpression != null) {
    val lambdaExpression = PsiTreeUtil.getParentOfType(element, PyLambdaExpression::class.java)

    if (lambdaExpression != null && callInnerReferenceExpression is PyQualifiedExpression) {
      if (lambdaExpression.parameterList.parameters.any { it.name == element.name }) {
        val callInnerReferencePyExpression = callInnerReferenceExpression as? PyQualifiedExpression
        return callInnerReferencePyExpression?.let { PyPsiUtils.getFirstQualifier(it) }
      }
    }
  }
  return null
}

fun processDataFrameColumns(dfName: String,
                            columns: Set<String>,
                            needValidatorCheck: Boolean,
                            elementOnPosition: PsiElement,
                            project: Project,
                            ignoreML: Boolean): List<LookupElement> {
  val validator = LanguageNamesValidation.INSTANCE.forLanguage(PythonLanguage.getInstance())
  return columns.mapNotNull { column ->
    when {
      !needValidatorCheck &&  elementOnPosition !is PyPlainStringElement -> {
        "'${StringUtil.escapeStringCharacters(column.length,  column, "'", StringBuilder())}'"
      }
      !needValidatorCheck -> StringUtil.escapeStringCharacters(column.length,  column, "'", StringBuilder())
      validator.isIdentifier(column, project) -> column
      else -> null
    }?.let {
      val lookupElement = LookupElementBuilder.create(it).withTypeText(PyBundle.message("pandas.completion.type.text", dfName))
      when (ignoreML) {
        true -> PrioritizedLookupElement.withPriority(lookupElement, DATAFRAME_COLUMN_PRIORITY).asMLIgnorable()
        false -> PrioritizedLookupElement.withPriority(lookupElement, DATAFRAME_COLUMN_PRIORITY)
      }
    }
  }
}

private fun LookupElement.asMLIgnorable(): LookupElement {
  return object : LookupElementDecorator<LookupElement>(this), MLRankingIgnorable {}
}
