// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast

import com.intellij.psi.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * A variable wrapper to be used in [UastVisitor].
 */
interface UVariable : UDeclaration, PsiVariable {
  @get:ApiStatus.ScheduledForRemoval
  @get:Deprecated("see the base property description")
  @Deprecated("see the base property description", ReplaceWith("javaPsi"))
  override val psi: PsiVariable

  @Suppress("DEPRECATION")
  private val javaPsiInternal
    get() = (this as? UVariableEx)?.javaPsi ?: psi

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
  override fun getInitializer(): PsiExpression? = javaPsiInternal.initializer

  override fun asLogString(): String = log("name = $name")

  override fun asRenderString(): String = buildString {
    if (uAnnotations.isNotEmpty()) {
      uAnnotations.joinTo(this, separator = " ", postfix = " ") { it.asRenderString() }
    }
    append(javaPsiInternal.renderModifiers())
    append("var ").append(javaPsiInternal.name).append(": ").append(javaPsiInternal.type.getCanonicalText(false))
    uastInitializer?.let { initializer -> append(" = " + initializer.asRenderString()) }
  }
}

interface UVariableEx : UVariable, UDeclarationEx {
  override val javaPsi: PsiVariable
}

private fun UVariable.visitContents(visitor: UastVisitor) {
  uAnnotations.acceptList(visitor)
  uastInitializer?.accept(visitor)
}

interface UParameter : UVariable, PsiParameter {
  @get:ApiStatus.ScheduledForRemoval
  @get:Deprecated("see the base property description")
  @Deprecated("see the base property description", ReplaceWith("javaPsi"))
  override val psi: PsiParameter

  override fun asLogString(): String = log("name = $name")

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitParameter(this)) return
    visitContents(visitor)
    visitor.afterVisitParameter(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R = visitor.visitParameter(this, data)
}

interface UParameterEx : UParameter, UDeclarationEx {
  override val javaPsi: PsiParameter
}

interface UField : UVariable, PsiField {
  @get:ApiStatus.ScheduledForRemoval
  @get:Deprecated("see the base property description")
  @Deprecated("see the base property description", ReplaceWith("javaPsi"))
  override val psi: PsiField

  override fun asLogString(): String = log("name = $name")

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitField(this)) return
    visitContents(visitor)
    visitor.afterVisitField(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R = visitor.visitField(this, data)
}

interface UFieldEx : UField, UDeclarationEx {
  override val javaPsi: PsiField
}

interface ULocalVariable : UVariable, PsiLocalVariable {
  @get:ApiStatus.ScheduledForRemoval
  @get:Deprecated("see the base property description")
  @Deprecated("see the base property description", ReplaceWith("javaPsi"))
  override val psi: PsiLocalVariable

  override fun asLogString(): String = log("name = $name")

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitLocalVariable(this)) return
    visitContents(visitor)
    visitor.afterVisitLocalVariable(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R = visitor.visitLocalVariable(this, data)
}

interface ULocalVariableEx : ULocalVariable, UDeclarationEx {
  override val javaPsi: PsiLocalVariable
}

interface UEnumConstant : UField, UCallExpression, PsiEnumConstant {
  @get:ApiStatus.ScheduledForRemoval
  @get:Deprecated("see the base property description")
  @Deprecated("see the base property description", ReplaceWith("javaPsi"))
  override val psi: PsiEnumConstant

  val initializingClass: UClass?

  override fun asLogString(): String = log("name = $name")

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitEnumConstant(this)) return
    uAnnotations.acceptList(visitor)
    methodIdentifier?.accept(visitor)
    classReference?.accept(visitor)
    valueArguments.acceptList(visitor)
    initializingClass?.accept(visitor)
    visitor.afterVisitEnumConstant(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitEnumConstantExpression(this, data)

  override fun asRenderString(): String = buildString {
    if (uAnnotations.isNotEmpty()) {
      uAnnotations.joinTo(this, separator = " ", postfix = " ", transform = UAnnotation::asRenderString)
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

interface UEnumConstantEx : UEnumConstant, UDeclarationEx {
  override val javaPsi: PsiEnumConstant
}