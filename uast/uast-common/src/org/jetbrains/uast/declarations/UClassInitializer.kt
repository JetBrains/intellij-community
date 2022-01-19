// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast

import com.intellij.psi.PsiClassInitializer
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * A class initializer wrapper to be used in [UastVisitor].
 */
interface UClassInitializer : UDeclaration, PsiClassInitializer {
  /**
   * Returns the body of this class initializer.
   */
  val uastBody: UExpression

  @Suppress("DEPRECATION")
  private val javaPsiInternal
    get() = (this as? UClassInitializerEx)?.javaPsi ?: psi


  override fun accept(visitor: UastVisitor) {
    if (visitor.visitInitializer(this)) return
    uAnnotations.acceptList(visitor)
    uastBody.accept(visitor)
    visitor.afterVisitInitializer(this)
  }

  override fun asRenderString(): String = buildString {
    append(modifierList)
    appendln(uastBody.asRenderString().withMargin)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitClassInitializer(this, data)

  override fun asLogString(): String = log("isStatic = $isStatic")
}

interface UClassInitializerEx : UClassInitializer, UDeclarationEx {
  override val javaPsi: PsiClassInitializer
}