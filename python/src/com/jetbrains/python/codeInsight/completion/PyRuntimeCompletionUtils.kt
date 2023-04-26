// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.completion.ml.MLRankingIgnorable
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.lang.LanguageNamesValidation
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeListener
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueGroupNodeImpl
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.debugger.PyXValueGroup
import com.jetbrains.python.debugger.pydev.ProcessDebugger
import com.jetbrains.python.debugger.values.DataFrameDebugValue
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl
import java.util.concurrent.CompletableFuture
import javax.swing.tree.TreeNode

/**
 * This data class stores information about a possible python object.
 * @param psiName - name of PsiElement, that could be a python object
 * @param needValidatorCheck - boolean flag to switch on check for validating completion items name
 * @param pyQualifiedExpressionList - represents a qualified expression in the list
 * LanguageNamesValidation.INSTANCE.forLanguage(PythonLanguage.getInstance()).isIdentifier()
 *
 * An example "a.b.c": psiName = "a", needValidatorCheck = false, pyQualifiedExpressionList = ["b","c"]
 */
data class PyObjectCandidate(val psiName: String, val needValidatorCheck: Boolean, val pyQualifiedExpressionList: List<String>)


// Temporary priority value to control order in CompletionResultSet (DS-3746)
private const val RUNTIME_COMPLETION_PRIORITY = 100.0

fun getCompleteAttribute(parameters: CompletionParameters): List<PyObjectCandidate> {

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

private fun createPandasDataFrameCandidate(psiElement: PsiElement, needValidatorCheck: Boolean): PyObjectCandidate {
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
          is PyStringElement -> {
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

  return PyObjectCandidate(firstChild.text, needValidatorCheck, columns)
}

private fun getPossibleObjectsDataFrame(parameters: CompletionParameters,
                                        callInnerReferenceExpression: PyExpression?): List<PyObjectCandidate> {
  return setOfNotNull(callInnerReferenceExpression?.text, getSliceSubscriptionReferenceExpression(parameters)?.text,
                      getAttributeReferenceExpression(parameters)?.text).map {

    PyObjectCandidate(it, false, emptyList())
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
  return when {
    exactElement != null -> Pair(exactElement, needCheck)

    else -> null
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

fun proceedPyValueChildrenNames(childrenNodes: Set<String>, ignoreML: Boolean = false): List<LookupElement> {
  return childrenNodes.map {
    val lookupElement = LookupElementBuilder.create(it).withTypeText(PyBundle.message("runtime.completion.type.text"))
    when (ignoreML) {
      true -> PrioritizedLookupElement.withPriority(lookupElement, RUNTIME_COMPLETION_PRIORITY).asMLIgnorable()
      false -> PrioritizedLookupElement.withPriority(lookupElement, RUNTIME_COMPLETION_PRIORITY)
    }
  }
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
      !needValidatorCheck && elementOnPosition !is PyStringElement -> {
        "'${StringUtil.escapeStringCharacters(column.length, column, "'", false, StringBuilder())}'"
      }
      !needValidatorCheck -> StringUtil.escapeStringCharacters(column.length, column, "'\"", false, StringBuilder())
      validator.isIdentifier(column, project) -> column
      else -> null
    }?.let {
      val lookupElement = LookupElementBuilder.create(it).withTypeText(PyBundle.message("pandas.completion.type.text", dfName))
      when (ignoreML) {
        true -> PrioritizedLookupElement.withPriority(lookupElement, RUNTIME_COMPLETION_PRIORITY).asMLIgnorable()
        false -> PrioritizedLookupElement.withPriority(lookupElement, RUNTIME_COMPLETION_PRIORITY)
      }
    }
  }
}

private fun LookupElement.asMLIgnorable(): LookupElement {
  return object : LookupElementDecorator<LookupElement>(this), MLRankingIgnorable {}
}


fun computeChildrenIfNeeded(valueNode: XValueContainerNode<*>) {
  if (!valueNode.isLeaf && valueNode.loadedChildren.isEmpty()) {
    val futureChildrenReady = CompletableFuture<Boolean>()
    val listener = object : XDebuggerTreeListener {
      override fun childrenLoaded(node: XDebuggerTreeNode, children: MutableList<out XValueContainerNode<*>>, last: Boolean) {
        if (node == valueNode) {
          futureChildrenReady.complete(true)
        }
      }
    }
    // Start to listen tree changes.
    valueNode.tree.addTreeListener(listener)
    invokeLater {
      valueNode.startComputingChildren()
    }
    // Additional check (children did not empty yet) to prevent race condition.
    if (valueNode.loadedChildren.isEmpty()) {
      // Wait until children will be added to the tree.
      futureChildrenReady.get()
    }
    // Stop to listen tree changes.
    valueNode.tree.removeTreeListener(listener)
  }
}

fun extractChildByName(childrenNodes: List<TreeNode>, name: String): XValueNodeImpl? {
  return childrenNodes.firstOrNull { it is XValueNodeImpl && it.name == name } as XValueNodeImpl?
}

fun getParentNodeByName(children: List<TreeNode>, psiName: String): XValueNodeImpl? {
  for (node in children) {
    if (node is XValueGroupNodeImpl && (node.valueContainer as PyXValueGroup).groupType == ProcessDebugger.GROUP_TYPE.SPECIAL) {
      computeChildrenIfNeeded(node)
      extractChildByName(node.loadedChildren, psiName)?.let {
        return it
      }
    }
    else if (node is XValueNodeImpl) {
      if (node.name == psiName) {
        return node
      }
    }
  }
  return null
}

fun getSetOfChildrenByListOfCall(valueNode: XValueNodeImpl?, listOfCall: List<String>): Pair<XValueNodeImpl, List<String>>? {
  var currentNode = valueNode ?: return null
  listOfCall.forEachIndexed { index, call ->
    when (currentNode.valueContainer) {
      is DataFrameDebugValue -> {
        return Pair(currentNode, listOfCall.subList(index, listOfCall.size))
      }
      else -> {
        computeChildrenIfNeeded(currentNode)
        currentNode = extractChildByName(currentNode.children, call) ?: return null
      }
    }
  }
  computeChildrenIfNeeded(currentNode)
  return Pair(currentNode, emptyList())
}

fun createCustomMatcher(parameters: CompletionParameters, result: CompletionResultSet): CompletionResultSet {
  val currentElement = parameters.position
  if (currentElement is PyStringElement) {
    val newPrefix = TextRange.create(currentElement.contentRange.startOffset,
                                     parameters.offset - currentElement.textRange.startOffset).substring(currentElement.text)
    return result.withPrefixMatcher(newPrefix)
  }
  return result
}
