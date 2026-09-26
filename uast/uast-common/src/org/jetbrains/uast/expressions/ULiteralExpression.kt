// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast

import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represents a literal expression.
 */
interface ULiteralExpression : UExpression {
  /**
   * Returns the literal expression value.
   * This is basically a String, Number or null if the literal is a `null` literal.
   */
  val value: Any?

  /**
   * Returns true if the literal is a `null`-literal, false otherwise.
   */
  val isNull: Boolean
    get() = value == null

  /**
   * Returns true if the literal is a [String] literal, false otherwise.
   */
  val isString: Boolean
    get() = evaluate() is String

  /**
   * Returns true if the literal is a [Boolean] literal, false otherwise.
   */
  val isBoolean: Boolean
    get() = evaluate() is Boolean

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitLiteralExpression(this)) return
    uAnnotations.acceptList(visitor)
    visitor.afterVisitLiteralExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitLiteralExpression(this, data)

  override fun asRenderString(): String {
    return when (val value = value) {
      null -> "null"
      is Char -> "'$value'"
      is String -> '"' + value.replace("\\", "\\\\")
        .replace("\r", "\\r").replace("\n", "\\n")
        .replace("\t", "\\t").replace("\b", "\\b")
        .replace("\"", "\\\"") + '"'
      else -> value.toString()
    }
  }

  override fun asLogString(): String = log("value = ${asRenderString()}")
}
