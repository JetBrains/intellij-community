// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast

import com.intellij.psi.PsiType
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represents an object literal expression, e.g. `new Runnable() {}` in Java.
 */
interface UObjectLiteralExpression : UCallExpression {
  /**
   * Returns the class declaration.
   */
  val declaration: UClass

  override val methodIdentifier: UIdentifier?
    get() = null

  override val kind: UastCallKind
    get() = UastCallKind.CONSTRUCTOR_CALL

  override val methodName: String?
    get() = null

  override val receiver: UExpression?
    get() = null

  override val receiverType: PsiType?
    get() = null

  override val returnType: PsiType?
    get() = null


  override fun accept(visitor: UastVisitor) {
    if (visitor.visitObjectLiteralExpression(this)) return
    uAnnotations.acceptList(visitor)
    valueArguments.acceptList(visitor)
    declaration.accept(visitor)
    visitor.afterVisitObjectLiteralExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitObjectLiteralExpression(this, data)

  override fun asLogString(): String = log()

  override fun asRenderString(): String = "anonymous " + declaration.text
}