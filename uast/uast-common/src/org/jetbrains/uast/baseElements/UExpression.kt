// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represents an expression or statement (which is considered as an expression in Uast).
 */
interface UExpression : UElement, UAnnotated {
  /**
   * Returns the expression value or null if the value can't be calculated.
   */
  fun evaluate(): Any? = null

  /**
   * Returns expression type, or null if type can not be inferred, or if this expression is a statement.
   */
  fun getExpressionType(): PsiType? = null

  override fun accept(visitor: UastVisitor) {
    visitor.visitElement(this)
    visitor.afterVisitElement(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R = visitor.visitExpression(this, data)
}

/**
 * Represents an annotated element.
 */
interface UAnnotated : UElement {

  /**
   * Returns the list of annotations applied to the current element.
   */
  val uAnnotations: List<UAnnotation>

  /**
   * Looks up for annotation element using the annotation qualified name.
   *
   * @param fqName the qualified name to search
   * @return the first annotation element with the specified qualified name, or null if there is no annotation with such name.
   */
  fun findAnnotation(fqName: String): UAnnotation? = uAnnotations.firstOrNull { it.qualifiedName == fqName }
}

/**
 * Represents a labeled element.
 */
interface ULabeled : UElement {
  /**
   * Returns the label name, or null if the label is empty.
   */
  val label: String?

  /**
   * Returns the label identifier, or null if the label is empty.
   */
  val labelIdentifier: UIdentifier?
}

/**
 * In some cases (user typing, syntax error) elements, which are supposed to exist, are missing.
 * The obvious example - the lack of the condition expression in [UIfExpression], e.g. 'if () return'.
 * [UIfExpression.condition] is required to return not-null values,
 *  and Uast implementation should return something instead of 'null' in this case.
 *
 * Use [UastEmptyExpression] in this case.
 */
open class UastEmptyExpression(override val uastParent: UElement?) : UExpression {

  override val uAnnotations: List<UAnnotation>
    get() = emptyList()

  @get:ApiStatus.ScheduledForRemoval
  @get:Deprecated("see the base property description")
  @Deprecated("see the base property description", ReplaceWith("sourcePsi"))
  override val psi: PsiElement?
    get() = null

  override fun asLogString(): String = log()

  override fun hashCode(): Int = uastParent?.hashCode() ?: super.hashCode()

  override fun equals(other: Any?): Boolean =
    if (other is UastEmptyExpression) other.uastParent == uastParent
    else false

  @Deprecated("create class instance instead")
  companion object : UastEmptyExpression(null) {
    @JvmField
    val INSTANCE: UastEmptyExpression = this
  }

}