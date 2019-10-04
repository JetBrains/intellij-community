// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast

import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represents a
 *
 * `for (element : collectionOfElements) {
 *      // body
 *  }`
 *
 *  loop expression.
 */
interface UForEachExpression : ULoopExpression {
  /**
   * Returns the loop variable.
   */
  val variable: UParameter

  /**
   * Returns the iterated value (collection, sequence, iterable etc.)
   */
  val iteratedValue: UExpression

  /**
   * Returns the identifier for the 'for' ('foreach') keyword.
   */
  val forIdentifier: UIdentifier

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitForEachExpression(this)) return
    uAnnotations.acceptList(visitor)
    iteratedValue.accept(visitor)
    body.accept(visitor)
    visitor.afterVisitForEachExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitForEachExpression(this, data)

  override fun asRenderString(): String = buildString {
    append("for (")
    append(variable.name)
    append(" : ")
    append(iteratedValue.asRenderString())
    append(") ")
    append(body.asRenderString())
  }

  override fun asLogString(): String = log()
}
