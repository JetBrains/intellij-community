// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast

import com.intellij.psi.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * A method visitor to be used in [UastVisitor].
 */
interface UMethod : UDeclaration, PsiMethod {
  @get:ApiStatus.ScheduledForRemoval
  @get:Deprecated("see the base property description")
  @Deprecated("see the base property description", ReplaceWith("javaPsi"))
  override val psi: PsiMethod

  override val javaPsi: PsiMethod

  /**
   * Returns the body expression (which can be also a [UBlockExpression]).
   */
  val uastBody: UExpression?

  /**
   * Returns the method parameters.
   */
  val uastParameters: List<UParameter>

  override fun getName(): String

  override fun getReturnType(): PsiType?

  override fun isConstructor(): Boolean

  @Deprecated("Use uastBody instead.", ReplaceWith("uastBody"))
  override fun getBody(): PsiCodeBlock? = javaPsi.body

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitMethod(this)) return
    uAnnotations.acceptList(visitor)
    uastParameters.acceptList(visitor)
    uastBody?.accept(visitor)
    visitor.afterVisitMethod(this)
  }

  override fun asRenderString(): String = buildString {
    if (uAnnotations.isNotEmpty()) {
      uAnnotations.joinTo(buffer = this, separator = "\n", postfix = "\n", transform = UAnnotation::asRenderString)
    }

    append(javaPsi.renderModifiers())
    append("fun ").append(name)

    uastParameters.joinTo(this, prefix = "(", postfix = ")") { parameter ->
      val annotationsText = if (parameter.uAnnotations.isNotEmpty())
        parameter.uAnnotations.joinToString(separator = " ", postfix = " ") { it.asRenderString() }
      else
        ""
      annotationsText + parameter.name + ": " + parameter.type.canonicalText
    }

    javaPsi.returnType?.let { append(" : " + it.canonicalText) }

    val body = uastBody
    append(when (body) {
             is UBlockExpression -> " " + body.asRenderString()
             else -> " = " + ((body ?: UastEmptyExpression(this@UMethod)).asRenderString())
           })
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitMethod(this, data)

  override fun asLogString(): String = log("name = $name")

  @JvmDefault
  val returnTypeReference: UTypeReferenceExpression?
    get() {
      val sourcePsi = sourcePsi ?: return null
      for (child in sourcePsi.children) {
        val expression = child.toUElement(UTypeReferenceExpression::class.java)
        if (expression != null) {
          return expression
        }
      }
      return null
    }

}

interface UAnnotationMethod : UMethod, PsiAnnotationMethod {
  @get:ApiStatus.ScheduledForRemoval
  @get:Deprecated("see the base property description")
  @Deprecated("see the base property description", ReplaceWith("javaPsi"))
  override val psi: PsiAnnotationMethod

  /**
   * Returns the default value of this annotation method.
   */
  val uastDefaultValue: UExpression?

  override fun getDefaultValue(): PsiAnnotationMemberValue? = (javaPsi as? PsiAnnotationMethod)?.defaultValue

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitMethod(this)) return
    uAnnotations.acceptList(visitor)
    uastParameters.acceptList(visitor)
    uastBody?.accept(visitor)
    uastDefaultValue?.accept(visitor)
    visitor.afterVisitMethod(this)
  }

  override fun asLogString(): String = log("name = $name")
}
