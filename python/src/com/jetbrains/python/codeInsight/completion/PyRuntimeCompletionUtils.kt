// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.ml.MLRankingIgnorable
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeListener
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueGroupNodeImpl
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.debugger.PyDebugValue
import com.jetbrains.python.debugger.PyXValueGroup
import com.jetbrains.python.debugger.pydev.ProcessDebugger
import com.jetbrains.python.debugger.values.DataFrameDebugValue
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl
import java.util.concurrent.CompletableFuture
import javax.swing.tree.TreeNode

data class PyQualifiedExpressionItem(val pyQualifiedName: String, val delimiter: IElementType)

/**
 * This data class stores information about a possible python object.
 * @param psiName - name of PsiElement, that could be a python object
 * LanguageNamesValidation.INSTANCE.forLanguage(PythonLanguage.getInstance()).isIdentifier()
 * @param pyQualifiedExpressionList - represents a qualified expression in the list
 * @see PyQualifiedExpressionItem
 *
 * An example "a.b.c.":
 *     psiName = PyQualifiedExpressionItem("a",PyTokenTypes.DOT),
 *     pyQualifiedExpressionList = [PyQualifiedExpressionItem("b",PyTokenTypes.DOT),PyQualifiedExpressionItem("c",PyTokenTypes.DOT)]
 */
data class PyObjectCandidate(val psiName: PyQualifiedExpressionItem,
                             val pyQualifiedExpressionList: List<PyQualifiedExpressionItem>)


// Temporary priority value to control order in CompletionResultSet (DS-3746)
private const val RUNTIME_COMPLETION_PRIORITY = 100.0

internal fun getCompleteAttribute(parameters: CompletionParameters): List<PyObjectCandidate> {

  val callInnerReferenceExpression = getCallInnerReferenceExpression(parameters)
  val (currentElement, lastDelimiter) = findCompleteAttribute(parameters) ?: return getPossibleObjectsDataFrame(parameters,
                                                                                                                callInnerReferenceExpression)

  return when (currentElement) {
    is PyCallExpression, is PyParenthesizedExpression -> {
      if (lastDelimiter == PyTokenTypes.DOT) {
        val qualifiedElement = PyQualifiedExpressionItem(currentElement.firstChild.text, CALL_DOT)
        return listOf(PyObjectCandidate(qualifiedElement, emptyList()))
      }
      emptyList()
    }
    is PyExpression -> buildList {
      val parentLambdaExpression = collectParentOfLambdaExpression(currentElement, callInnerReferenceExpression)
      parentLambdaExpression?.let { expression ->
        createPyObjectCandidate(expression, lastDelimiter)?.let { add(it) }
      }
      createPyObjectCandidate(currentElement, lastDelimiter)?.let { add(it) }
    }
    else -> {
      emptyList()
    }
  }
}

private fun getPyElementText(child: PsiElement?): String? {
  child ?: return null
  if (child.elementType != PyTokenTypes.DOT && child.elementType != PyTokenTypes.LBRACKET && child.elementType != PyTokenTypes.RBRACKET) {
    return when (child) {
      is PyStringLiteralExpression -> {
        return (child as PyStringLiteralExpressionImpl).stringValue
      }
      is PyStringElement -> {
        child.content
      }
      else -> {
        child.text
      }
    }
  }
  return null
}

private val CALL_DOT = PyElementType("CALL_DOT")
val pattern = listOf(PyTokenTypes.LPAR, PyTokenTypes.RPAR, PyTokenTypes.DOT)

private fun createPyObjectCandidate(psiElement: PsiElement, lastDelimiter: IElementType): PyObjectCandidate? {
  val names = mutableListOf<String>()
  val delimiter = mutableListOf<IElementType>()

  val firstChild: PsiElement = PsiTreeUtil.getDeepestFirst(psiElement)
  val lastChild = PsiTreeUtil.getDeepestLast(psiElement)

  val allChildren = generateSequence(firstChild) { child ->
    if (child == lastChild) null else PsiTreeUtil.nextLeaf(child)
  }.toList()

  var index = 0
  while (index < allChildren.size) {
    val elementType = allChildren[index].elementType ?: continue
    when (elementType) {
      PyTokenTypes.LPAR -> {
        if (allChildren.size > index + pattern.size && allChildren.subList(index, index + pattern.size).map { it.elementType } == pattern) {
          delimiter.add(CALL_DOT)
          index += 3
        }
        else {
          delimiter.add(elementType)
          index += 1
        }
      }
      PyTokenTypes.RBRACKET -> {
        if (delimiter.last() != PyTokenTypes.LBRACKET) {
          delimiter.add(elementType)
        }
        index += 1
      }
      PyTokenTypes.DOT, PyTokenTypes.LBRACKET -> {
        delimiter.add(elementType)
        index += 1
      }
      else -> {
        val elementText = getPyElementText(allChildren[index]) ?: return null
        names.add(elementText)
        index += 1
      }
    }
  }
  delimiter.add(lastDelimiter)

  if (names.isNotEmpty() && delimiter.size == names.size) {
    val firstDelimiter = delimiter.removeFirst()
    val firstName = names.removeFirst()
    return PyObjectCandidate(PyQualifiedExpressionItem(firstName, firstDelimiter),
                             names.zip(delimiter).map { pair ->
                               PyQualifiedExpressionItem(pair.first, pair.second)
                             })
  }

  return PyObjectCandidate(PyQualifiedExpressionItem(firstChild.text, lastDelimiter), emptyList())
}

private fun getPossibleObjectsDataFrame(parameters: CompletionParameters,
                                        callInnerReferenceExpression: PyExpression?): List<PyObjectCandidate> {
  return setOfNotNull(callInnerReferenceExpression?.text, getSliceSubscriptionReferenceExpression(parameters)?.text,
                      getAttributeReferenceExpression(parameters)?.text).map {

    PyObjectCandidate(PyQualifiedExpressionItem(it, PyTokenTypes.LBRACKET), emptyList())
  }
}

private fun findCompleteAttribute(parameters: CompletionParameters): Pair<PsiElement, IElementType>? {
  var element = parameters.position
  val delimiter: IElementType?

  if (element.prevSibling?.elementType != PyTokenTypes.DOT && element.parent?.prevSibling?.elementType == PyTokenTypes.LBRACKET) {
    delimiter = element.parent?.prevSibling?.elementType
    element = element.parent
  }
  else {
    delimiter = element.prevSibling?.elementType
  }

  val exactElement = element.prevSibling?.prevSibling
  return when {
    exactElement != null && delimiter != null -> Pair(exactElement, delimiter)

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

internal fun createPrioritizedLookupElement(lookupElement: LookupElement, ignoreML: Boolean): LookupElement {
  val prioritizedElement = PrioritizedLookupElement.withPriority(lookupElement, RUNTIME_COMPLETION_PRIORITY)
  if (ignoreML) {
    return prioritizedElement.asMLIgnorable()
  }
  return prioritizedElement
}

private fun LookupElement.asMLIgnorable(): LookupElement {
  return object : LookupElementDecorator<LookupElement>(this), MLRankingIgnorable {}
}


internal fun computeChildrenIfNeeded(valueNode: XValueContainerNode<*>) {
  if (!valueNode.isLeaf && valueNode.loadedChildren.isEmpty()) {
    val futureChildrenReady = CompletableFuture<Boolean>()
    val listener = object : XDebuggerTreeListener {
      override fun childrenLoaded(node: XDebuggerTreeNode, children: MutableList<out XValueContainerNode<*>>, last: Boolean) {
        if (node == valueNode) {
          futureChildrenReady.complete(true)
        }
      }
    }
    // Start to listen to tree changes.
    valueNode.tree.addTreeListener(listener)
    invokeLater {
      valueNode.startComputingChildren()
    }
    // Additional check (children did not empty yet) to prevent race condition.
    if (valueNode.loadedChildren.isEmpty()) {
      // Wait until children will be added to the tree.
      futureChildrenReady.get()
    }
    // Stop to listen to tree changes.
    valueNode.tree.removeTreeListener(listener)
  }
}

private fun extractChildByName(currentNode: XValueContainerNode<*>, childrenNodes: List<TreeNode>, name: String): XValueNodeImpl? {
  if ((currentNode.valueContainer as? PyDebugValue)?.qualifiedType == "builtins.dict") {
    return childrenNodes.firstOrNull { it is XValueNodeImpl && it.name == "'${name}'" } as XValueNodeImpl?
  }
  return childrenNodes.firstOrNull { it is XValueNodeImpl && it.name == name } as XValueNodeImpl?
}

internal fun getParentNodeByName(children: List<TreeNode>, psiName: String, completionType: CompletionType): XValueNodeImpl? {
  /**
   * For preventing an extra load of "Special variables".
   * Firstly, looking through loaded variables and if not found - load values inside the group (make a request to jupyter server).
   */
  val globalVariables = children.filterIsInstance<XValueNodeImpl>()
  globalVariables.forEach { node ->
    if (node.name == psiName) {
      return node
    }
  }
  if (completionType == CompletionType.BASIC) return null
  val specialVariables = children.filterIsInstance<XValueGroupNodeImpl>()
    .filter { node -> (node.valueContainer as PyXValueGroup).groupType == ProcessDebugger.GROUP_TYPE.SPECIAL }
  specialVariables.forEach { node ->
    computeChildrenIfNeeded(node)
    extractChildByName(node, node.loadedChildren, psiName)?.let {
      return it
    }
  }
  return null
}

internal val typeToDelimiter = mapOf(
  "polars.internals.dataframe.frame.DataFrame" to setOf(PyTokenTypes.LBRACKET),
  "polars.dataframe.frame.DataFrame" to setOf(PyTokenTypes.LBRACKET),
  "pandas.core.frame.DataFrame" to setOf(PyTokenTypes.LBRACKET, PyTokenTypes.DOT),
  "builtins.dict" to setOf(PyTokenTypes.LBRACKET)
)

internal fun checkDelimiterByType(qualifiedType: String?, delimiter: IElementType): Boolean {
  qualifiedType ?: return false
  val delimiters = typeToDelimiter[qualifiedType]
  return delimiters != null && delimiter !in delimiters
}

internal fun getSetOfChildrenByListOfCall(valueNode: XValueNodeImpl?,
                                          listOfCall: List<PyQualifiedExpressionItem>,
                                          completionType: CompletionType): Pair<XValueNodeImpl, List<PyQualifiedExpressionItem>>? {
  var currentNode = valueNode ?: return null

  listOfCall.forEachIndexed { index, call ->
    when (currentNode.valueContainer) {
      is DataFrameDebugValue -> {
        return Pair(currentNode, listOfCall.subList(index, listOfCall.size))
      }
      else -> {
        if (completionType == CompletionType.BASIC) return null
        computeChildrenIfNeeded(currentNode)
        currentNode = extractChildByName(currentNode, currentNode.children, call.pyQualifiedName) ?: return null
      }
    }
    val valueContainer = currentNode.valueContainer
    if (valueContainer is PyDebugValue) {
      if (checkDelimiterByType(valueContainer.qualifiedType, call.delimiter)) return null
    }
  }
  return Pair(currentNode, emptyList())
}

internal fun createCustomMatcher(parameters: CompletionParameters, result: CompletionResultSet): PrefixMatcher {
  val currentElement = parameters.position
  if (currentElement is PyStringElement) {
    val newPrefix = TextRange.create(currentElement.contentRange.startOffset,
                                     parameters.offset - currentElement.textRange.startOffset).substring(currentElement.text)
    return PlainPrefixMatcher(newPrefix)
  }
  return PlainPrefixMatcher(result.prefixMatcher.prefix)
}
