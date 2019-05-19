/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.uast

import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represents a `break` expression.
 */
interface UBreakExpression : UJumpExpression {

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitBreakExpression(this)) return
    annotations.acceptList(visitor)
    visitor.afterVisitBreakExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitBreakExpression(this, data)

  override fun asLogString(): String = log("label = $label")

  override fun asRenderString(): String = label?.let { "break@$it" } ?: "break"
}

interface UBreakWithValueExpression : UBreakExpression {

  val valueExpression: UExpression?

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitBreakExpression(this)) return
    annotations.acceptList(visitor)
    valueExpression?.accept(visitor)
    visitor.afterVisitBreakExpression(this)
  }

  override fun asLogString(): String = log("label = $label, hasValue = ${valueExpression != null}")

  override fun asRenderString(): String = buildString {
    append("break")
    label?.let { append("@$it") }
    valueExpression?.let { append(" ${it.asRenderString()}") }
  }

}