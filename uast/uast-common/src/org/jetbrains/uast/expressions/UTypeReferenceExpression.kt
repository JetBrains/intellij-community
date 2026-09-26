// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast

import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

interface UTypeReferenceExpression : UExpression {
  /**
   * Returns the resolved type for this reference.
   */
  val type: PsiType

  /**
   * Returns the qualified name of the class type, or null if the [type] is not a class type.
   */
  fun getQualifiedName(): String? = PsiTypesUtil.getPsiClass(type)?.qualifiedName

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitTypeReferenceExpression(this)) return
    uAnnotations.acceptList(visitor)
    visitor.afterVisitTypeReferenceExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitTypeReferenceExpression(this, data)

  override fun asLogString(): String = log("name = ${type.name}")

  override fun asRenderString(): String = type.name
}