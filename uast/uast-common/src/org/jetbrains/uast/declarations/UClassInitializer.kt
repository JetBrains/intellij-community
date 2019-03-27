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

import com.intellij.psi.PsiClassInitializer
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * A class initializer wrapper to be used in [UastVisitor].
 */
interface UClassInitializer : UDeclaration, PsiClassInitializer {

  @Suppress("OverridingDeprecatedMember")
  override val psi: PsiClassInitializer

  /**
   * Returns the body of this class initializer.
   */
  val uastBody: UExpression

  @Suppress("DEPRECATION")
  private val javaPsiInternal
    get() = (this as? UClassInitializerEx)?.javaPsi ?: psi


  override fun accept(visitor: UastVisitor) {
    if (visitor.visitInitializer(this)) return
    annotations.acceptList(visitor)
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