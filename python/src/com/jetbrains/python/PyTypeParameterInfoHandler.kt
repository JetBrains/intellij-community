// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoHandler
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.ParameterInfoUIContextEx
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.codeInsight.parameterInfo.PyTypeParameterInfoUtil
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTupleExpression
import com.jetbrains.python.psi.types.TypeEvalContext
import java.util.EnumSet

/**
 * Shows the "Parameter Info" popup with the expected type parameters when the caret is inside the square brackets of a
 * parameterized type, e.g. `Generator[<caret>]` displays `_YieldT_co, _SendT_contra, _ReturnT_co`.
 *
 * This is a separate handler from [PyParameterInfoHandler] (which handles call argument lists) and is registered with a
 * higher priority so that a generic subscription nested in a call, such as `cast(list[<caret>], x)`, shows the type
 * parameters of the subscription rather than the parameters of the enclosing call.
 */
class PyTypeParameterInfoHandler : ParameterInfoHandler<PySubscriptionExpression, PyTypeParameterInfoHandler.TypeParameterListPresentation> {

  class TypeParameterListPresentation(val representations: List<String>)

  override fun findElementForParameterInfo(context: CreateParameterInfoContext): PySubscriptionExpression? {
    val subscription = findTypeParameterList(context.file, context.offset) ?: return null
    val typeEvalContext = TypeEvalContext.userInitiated(context.project, context.file)
    val representations = PyTypeParameterInfoUtil.getTypeParameterRepresentations(subscription, typeEvalContext)
    if (representations.isEmpty()) return null
    context.itemsToShow = arrayOf<Any>(TypeParameterListPresentation(representations))
    return subscription
  }

  override fun showParameterInfo(element: PySubscriptionExpression, context: CreateParameterInfoContext) {
    context.showHint(element, element.textRange.startOffset, this)
  }

  override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): PySubscriptionExpression? {
    return findTypeParameterList(context.file, context.offset)
  }

  override fun updateParameterInfo(parameterOwner: PySubscriptionExpression, context: UpdateParameterInfoContext) {
    context.setCurrentParameter(currentTypeParameterIndex(parameterOwner, context.offset))
  }

  override fun updateUI(p: TypeParameterListPresentation, context: ParameterInfoUIContext) {
    val representations = p.representations
    val size = representations.size
    if (size == 0) return
    val highlighted = highlightedIndex(context.currentParameterIndex, representations)

    if (context is ParameterInfoUIContextEx) {
      val texts = Array(size) { i -> if (i < size - 1) representations[i] + ", " else representations[i] }
      val flags = Array(size) { i ->
        if (i == highlighted) EnumSet.of(ParameterInfoUIContextEx.Flag.HIGHLIGHT)
        else EnumSet.noneOf(ParameterInfoUIContextEx.Flag::class.java)
      }
      context.setupUIComponentPresentation(texts, flags, context.defaultParameterColor)
    }
    else {
      context.setupUIComponentPresentation(representations.joinToString(", "), -1, 0, false, false, false,
                                           context.defaultParameterColor)
    }
  }
}

private fun findTypeParameterList(file: PsiFile, offset: Int): PySubscriptionExpression? {
  val element = file.findElementAt(if (offset > 0) offset - 1 else offset) ?: return null
  val subscription = PsiTreeUtil.getParentOfType(element, PySubscriptionExpression::class.java, false) ?: return null
  // The caret has to be inside the square brackets, not on the operand or past the closing bracket.
  val leftBracket = subscription.node.findChildByType(PyTokenTypes.LBRACKET) ?: return null
  if (offset <= leftBracket.startOffset) return null
  val rightBracket = subscription.node.findChildByType(PyTokenTypes.RBRACKET)
  if (rightBracket != null && offset > rightBracket.startOffset) return null
  return subscription
}

private fun currentTypeParameterIndex(subscription: PySubscriptionExpression, offset: Int): Int {
  // A single type argument has no comma, so it always maps to the first type parameter.
  val index = subscription.indexExpression as? PyTupleExpression ?: return 0
  var slot = 0
  var child = index.node.firstChildNode
  while (child != null) {
    if (child.elementType === PyTokenTypes.COMMA && child.startOffset < offset) slot++
    child = child.treeNext
  }
  return slot
}

private fun highlightedIndex(current: Int, representations: List<String>): Int {
  val size = representations.size
  if (current in 0 until size) return current
  // A trailing TypeVarTuple/ParamSpec (rendered with a leading '*') absorbs any extra positional type arguments.
  if (current >= size && representations.lastOrNull()?.startsWith("*") == true) return size - 1
  return -1
}
