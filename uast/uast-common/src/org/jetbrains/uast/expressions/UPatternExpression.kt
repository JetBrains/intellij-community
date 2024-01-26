// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast

import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * A pattern matching expression.
 * Examples (Java):
 * * Type pattern: `Point p`
 * * Deconstruction pattern: `Point(int x, int y)` or `Point(int _, int _)` (variables are unnamed)
 * * Unnamed pattern: `Point(_, _)`
 */
@Experimental
interface UPatternExpression : UExpression {
  /**
   * Name of the pattern, can be null if the pattern is unnamed or no name is specified.
   */
  val name: String?

  /**
   * The primary type reference that is checked when evaluating this pattern or null when there is none.
   * * For deconstruction patterns like `Point(int x, int y)` this will be then main type `Point`.
   */
  val typeReference: UTypeReferenceExpression?

  /**
   * The deconstructed patterns or empty if this pattern is not a deconstruction pattern.
   */
  val deconstructedPatterns: List<UPatternExpression>

  override fun asLogString(): String = log()

  override fun asRenderString(): String {
    val renderPatternList = if (deconstructedPatterns.isNotEmpty()) "(${deconstructedPatterns.joinToString { it.asRenderString() }})" else ""
    val renderName = "${if (typeReference != null) " " else ""}${name ?: "_"}"
    return "${typeReference?.type?.name ?: ""}$renderPatternList$renderName"
  }

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitPatternExpression(this)) return
    uAnnotations.acceptList(visitor)
    typeReference?.accept(visitor)
    deconstructedPatterns.acceptList(visitor)
    visitor.afterVisitPatternExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R = visitor.visitPatternExpression(this, data)
}