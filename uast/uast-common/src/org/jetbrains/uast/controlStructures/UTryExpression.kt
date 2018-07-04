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

import com.intellij.psi.PsiType
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represents
 *
 * `try {
 *      // tryClause body
 *  } catch (e: Type1, Type2 ... TypeN) {
 *      // catchClause1 body
 *  } ... {
 *  finally {
 *      //finallyBody
 *  }`
 *
 *  and
 *
 *  `try (resource1, ..., resourceN) {
 *      // tryClause body
 *  }`
 *
 *  expressions.
 */
interface UTryExpression : UExpression {
  /**
   * Returns `true` if the try expression is a try-with-resources expression.
   */
  val hasResources: Boolean

  /**
   * Returns the list of resource variables declared in this expression, or an empty list if this expression is not a `try-with-resources` expression.
   */
  val resourceVariables: List<UVariable>

  /**
   * Returns the `try` clause expression.
   */
  val tryClause: UExpression

  /**
   * Returns the `catch` clauses [UCatchClause] expression list.
   */
  val catchClauses: List<UCatchClause>

  /**
   * Returns the `finally` clause expression, or null if the `finally` clause is absent.
   */
  val finallyClause: UExpression?

  /**
   * Returns an identifier for the 'try' keyword.
   */
  val tryIdentifier: UIdentifier

  /**
   * Returns an identifier for the 'finally' keyword, or null if the 'try' expression has not a 'finally' clause.
   */
  val finallyIdentifier: UIdentifier?

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitTryExpression(this)) return
    annotations.acceptList(visitor)
    resourceVariables.acceptList(visitor)
    tryClause.accept(visitor)
    catchClauses.acceptList(visitor)
    finallyClause?.accept(visitor)
    visitor.afterVisitTryExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitTryExpression(this, data)

  override fun asRenderString(): String = buildString {
    append("try ")
    if (hasResources) {
      append("(")
      append(resourceVariables.joinToString("\n") { it.asRenderString() })
      append(")")
    }
    appendln(tryClause.asRenderString().trim('\n', '\r'))
    catchClauses.forEach { appendln(it.asRenderString().trim('\n', '\r')) }
    finallyClause?.let { append("finally ").append(it.asRenderString().trim('\n', '\r')) }
  }

  override fun asLogString(): String = log(if (hasResources) "with resources" else "")
}

/**
 * Represents the `catch` clause in [UTryExpression].
 */
interface UCatchClause : UElement {
  /**
   * Returns the `catch` clause body expression.
   */
  val body: UExpression

  /**
   * Returns the exception parameter variables for this `catch` clause.
   */
  val parameters: List<UParameter>

  /**
   * Returns the exception type references for this `catch` clause.
   */
  val typeReferences: List<UTypeReferenceExpression>

  /**
   * Returns the expression types for this `catch` clause.
   */
  val types: List<PsiType>
    get() = typeReferences.map { it.type }

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitCatchClause(this)) return
    body.accept(visitor)
    visitor.afterVisitCatchClause(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitCatchClause(this, data)

  override fun asLogString(): String = log(parameters.joinToString { it.name ?: "<error>" })

  override fun asRenderString(): String = "catch (e) " + body.asRenderString()
}