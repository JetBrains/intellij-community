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

import com.intellij.psi.*
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * A variable wrapper to be used in [UastVisitor].
 */
interface UVariable : UDeclaration, PsiVariable {
  override val psi: PsiVariable

  /**
   * Returns the variable initializer or the parameter default value, or null if the variable has not an initializer.
   */
  val uastInitializer: UExpression?

  /**
   * Returns variable type reference.
   */
  val typeReference: UTypeReferenceExpression?

  override fun getType(): PsiType

  override fun getName(): String?

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitVariable(this)) return
    visitContents(visitor)
    visitor.afterVisitVariable(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitVariable(this, data)

  @Deprecated("Use uastInitializer instead.", ReplaceWith("uastInitializer"))
  override fun getInitializer(): PsiExpression? = psi.initializer

  override fun asLogString(): String = log("name = $name")

  override fun asRenderString(): String = buildString {
    if (annotations.isNotEmpty()) {
      annotations.joinTo(this, separator = " ", postfix = " ") { it.asRenderString() }
    }
    append(psi.renderModifiers())
    append("var ").append(psi.name).append(": ").append(psi.type.getCanonicalText(false))
    uastInitializer?.let { initializer -> append(" = " + initializer.asRenderString()) }
  }
}

/**
 * @since 2018.2
 */
interface UVariableEx : UVariable, UDeclarationEx {
  override val javaPsi: PsiVariable
}

private fun UVariable.visitContents(visitor: UastVisitor) {
  annotations.acceptList(visitor)
  uastInitializer?.accept(visitor)
}

interface UParameter : UVariable, PsiParameter {
  override val psi: PsiParameter

  override fun asLogString(): String = log("name = $name")

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitParameter(this)) return
    visitContents(visitor)
    visitor.afterVisitParameter(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R = visitor.visitParameter(this, data)
}

/**
 * @since 2018.2
 */
interface UParameterEx : UParameter, UDeclarationEx {
  override val javaPsi: PsiParameter
}

interface UField : UVariable, PsiField {
  override val psi: PsiField

  override fun asLogString(): String = log("name = $name")

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitField(this)) return
    visitContents(visitor)
    visitor.afterVisitField(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R = visitor.visitField(this, data)
}

/**
 * @since 2018.2
 */
interface UFieldEx : UField, UDeclarationEx {
  override val javaPsi: PsiField
}

interface ULocalVariable : UVariable, PsiLocalVariable {
  override val psi: PsiLocalVariable

  override fun asLogString(): String = log("name = $name")

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitLocalVariable(this)) return
    visitContents(visitor)
    visitor.afterVisitLocalVariable(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R = visitor.visitLocalVariable(this, data)
}

/**
 * @since 2018.2
 */
interface ULocalVariableEx : ULocalVariable, UDeclarationEx {
  override val javaPsi: PsiLocalVariable
}

interface UEnumConstant : UField, UCallExpression, PsiEnumConstant {
  override val psi: PsiEnumConstant

  val initializingClass: UClass?

  override fun asLogString(): String = log("name = $name")

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitEnumConstant(this)) return
    annotations.acceptList(visitor)
    methodIdentifier?.accept(visitor)
    classReference?.accept(visitor)
    valueArguments.acceptList(visitor)
    initializingClass?.accept(visitor)
    visitor.afterVisitEnumConstant(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitEnumConstantExpression(this, data)

  override fun asRenderString(): String = buildString {
    if (annotations.isNotEmpty()) {
      annotations.joinTo(this, separator = " ", postfix = " ", transform = UAnnotation::asRenderString)
    }
    append(name)
    if (valueArguments.isNotEmpty()) {
      valueArguments.joinTo(this, prefix = "(", postfix = ")", transform = UExpression::asRenderString)
    }
    initializingClass?.let {
      appendln(" {")
      it.uastDeclarations.forEach { declaration ->
        appendln(declaration.asRenderString().withMargin)
      }
      append("}")
    }
  }
}

/**
 * @since 2018.2
 */
interface UEnumConstantEx : UEnumConstant, UDeclarationEx {
  override val javaPsi: PsiEnumConstant
}