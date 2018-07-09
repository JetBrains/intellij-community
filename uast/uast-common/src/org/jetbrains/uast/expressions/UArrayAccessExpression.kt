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
 * Represents `receiver[index0, ..., indexN]` expression.
 */
interface UArrayAccessExpression : UExpression {
  /**
   * Returns the receiver expression.
   */
  val receiver: UExpression

  /**
   * Returns the list of index expressions.
   */
  val indices: List<UExpression>

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitArrayAccessExpression(this)) return
    annotations.acceptList(visitor)
    receiver.accept(visitor)
    indices.acceptList(visitor)
    visitor.afterVisitArrayAccessExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitArrayAccessExpression(this, data)

  override fun asLogString(): String = log()

  override fun asRenderString(): String = receiver.asRenderString() +
                                          indices.joinToString(prefix = "[", postfix = "]") { it.asRenderString() }
}