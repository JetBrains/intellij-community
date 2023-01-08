// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast

import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor

interface UReferenceExpression : UExpression, UResolvable {
  /**
   * Returns the resolved name for this reference, or null if the reference can't be resolved.
   */
  val resolvedName: String?

  override fun asLogString(): String = log()

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R = visitor.visitReferenceExpression(this, data)

  val referenceNameElement: UElement?
    get() = null

}

tailrec fun unwrapReferenceNameElement(element: UElement?): UElement? {
  when (element) {
    is UReferenceExpression -> {
      val referenceNameElement = element.referenceNameElement ?: return element
      if (referenceNameElement == element) return element
      return unwrapReferenceNameElement(referenceNameElement)
    }
    else -> return element
  }
}