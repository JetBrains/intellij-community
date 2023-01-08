// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast

import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

@JvmField
val EMPTY_ARRAY: Array<UElement> = emptyArray()
/**
 * The common interface for all Uast elements.
 */
interface UElement {
  /**
   * Returns the element parent.
   */
  val uastParent: UElement?

  /**
   * Returns the PSI element underlying this element. Note that some UElements are synthetic and do not have
   * an underlying PSI element; this doesn't mean that they are invalid.
   *
   * **Node for implementors**: please implement both [sourcePsi] and [javaPsi] fields or make them return `null` explicitly
   * if implementing is not possible. Redirect `psi` to one of them keeping existing behavior, use [sourcePsi] if nothing else is specified.
   */
  @Deprecated("ambiguous psi element, use `sourcePsi` or `javaPsi`", ReplaceWith("javaPsi"))
  val psi: PsiElement?


  /**
   * Returns the PSI element in original (physical) tree to which this UElement corresponds.
   * **Note**: that some UElements are synthetic and do not have an underlying PSI element;
   * this doesn't mean that they are invalid.
   */
  @Suppress("DEPRECATION")
  val sourcePsi: PsiElement?
    get() = psi

  /**
   * Returns the element which try to mimic Java-api psi element: [com.intellij.psi.PsiClass], [com.intellij.psi.PsiMethod] or [com.intellij.psi.PsiAnnotation] etc.
   * Will return null if this UElement doesn't have Java representation or it is not implemented.
   */
  @Suppress("DEPRECATION")
  val javaPsi: PsiElement?
    get() = psi

  /**
   * Returns true if this element is valid, false otherwise.
   */
  val isPsiValid: Boolean
    get() = sourcePsi?.isValid ?: true

  /**
   * Returns the list of comments for this element.
   */
  val comments: List<UComment>
    get() = emptyList()

  /**
   * Returns the log string (usually one line containing the class name and some additional information).
   *
   * Examples:
   * UWhileExpression
   * UBinaryExpression (>)
   * UCallExpression (println)
   * USimpleReferenceExpression (i)
   * ULiteralExpression (5)
   *
   * @return the expression tree for this element.
   * @see [UIfExpression] for example.
   */
  fun asLogString(): String

  /**
   * Returns the string in pseudo-code.
   *
   * Output example (should be something like this):
   * while (i > 5) {
   *     println("Hello, world")
   *     i--
   * }
   *
   * @return the rendered text.
   * @see [UIfExpression] for example.
   */
  fun asRenderString(): String = asLogString()

  /**
   * Returns the string as written in the source file.
   * Use this String only for logging and diagnostic text messages.
   *
   * @return the original text.
   */
  fun asSourceString(): String = asRenderString()

  /**
   * Passes the element to the specified visitor.
   *
   * @param visitor the visitor to pass the element to.
   */
  fun accept(visitor: UastVisitor) {
    visitor.visitElement(this)
    visitor.afterVisitElement(this)
  }

  /**
   * Passes the element to the specified typed visitor.
   *
   * @param visitor the visitor to pass the element to.
   */
  fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R = visitor.visitElement(this, data)


  /**
   * NOTE: it is called `lang` instead of "language" to avoid clash with [PsiElement.getLanguage] in classes which implements both interfaces,
   * @return language of the physical [PsiElement] this [UElement] was made from, or `UAST` language if no "physical" language could be found
   */
  val lang: Language
    get() = withContainingElements.mapNotNull { it.sourcePsi }.firstOrNull()?.language
            // ok. another try
            ?: withContainingElements.mapNotNull { it.getContainingUFile()?.sourcePsi?.language }.firstOrNull()
            // UAST in the end, hope it will never happen
            ?: Language.findLanguageByID("UAST")!!
}

val UElement?.sourcePsiElement: PsiElement?
  get() = this?.sourcePsi


@Suppress("UNCHECKED_CAST")
fun <T : PsiElement> UElement?.getAsJavaPsiElement(clazz: Class<T>): T? =
  this?.javaPsi?.takeIf { clazz.isAssignableFrom(it.javaClass) } as? T

/**
 * Returns a sequence including this element and its containing elements.
 */
val UElement.withContainingElements: Sequence<UElement>
  get() = generateSequence(this, UElement::uastParent)
