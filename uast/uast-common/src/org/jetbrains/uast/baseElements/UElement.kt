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

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

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
  val sourcePsi: PsiElement?
    get() = psi

  /**
   * Returns the element which try to mimic Java-api psi element: [com.intellij.psi.PsiClass], [com.intellij.psi.PsiMethod] or [com.intellij.psi.PsiAnnotation] etc.
   * Will return null if this UElement doesn't have Java representation or it is not implemented.
   */
  val javaPsi: PsiElement?
    get() = psi

  /**
   * Returns true if this element is valid, false otherwise.
   */
  val isPsiValid: Boolean
    get() = psi?.isValid ?: true

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
}

@Deprecated("No use anymore, all declarations were moved to UElement. To be removed in 2018.2")
interface JvmDeclarationUElement : UElement

/**
 * Experimental API
 */
val UElement?.sourcePsiElement: PsiElement?
  get() = fun(): PsiElement? {
    val element = (this as? JvmDeclarationUElement)?.sourcePsi ?: return null;

    // All following is a workaround for KT-21025 when returned `sourcePsi` is not actually a source psi
    // and also it is a copy of a similar hack in `AbstractBaseUastLocalInspectionTool` in 173-branch
    // Refer IDEA-CR-25636 and IDEA-CR-25766
    val desiredFile = this?.getContainingUFile()?.psi ?: return element

    fun inFile(element: PsiElement): Boolean {
      val file = element.containingFile ?: return false
      return file.viewProvider === desiredFile.viewProvider
    }

    if (inFile(element)) return element
    val navigationElement = element.navigationElement ?: return element
    if (inFile(navigationElement)) return navigationElement

    // last resort
    val elementAtSamePosition = desiredFile.findElementAt(navigationElement.textRange.startOffset)
    return if (elementAtSamePosition != null && elementAtSamePosition.text == navigationElement.text) {
      elementAtSamePosition
    }
    else element // it can't be helped
  }()


@ApiStatus.Experimental
@SuppressWarnings("unchecked")
fun <T : PsiElement> UElement?.getAsJavaPsiElement(clazz: Class<T>): T? = when (this) {
  is JvmDeclarationUElement -> this.javaPsi
  else -> this?.psi
}?.takeIf { clazz.isAssignableFrom(it.javaClass) } as? T

/**
 * Returns a sequence including this element and its containing elements.
 */
val UElement.withContainingElements: Sequence<UElement>
  get() = generateSequence(this, UElement::uastParent)
