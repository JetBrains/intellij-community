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
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.uast.visitor.UastTypedVisitor

/**
 * A [PsiElement] declaration wrapper.
 */
interface UDeclaration : UElement, PsiModifierListOwner, UAnnotated {
  /**
   * Returns the original declaration (which is *always* unwrapped, never a [UDeclaration]).
   */
  override val psi: PsiModifierListOwner

  override fun getOriginalElement(): PsiElement? = psi.originalElement

  /**
   * Returns the declaration name identifier. If declaration is anonymous other implementation dependant psi element will be returned.
   * The main rule that returned element is "anchor": it is a single token which represents this declaration.
   *
   * It is useful for putting gutters and inspection reports.
   */
  val uastAnchor: UElement?

  /**
   * Returns `true` if this declaration has a [PsiModifier.STATIC] modifier.
   */
  val isStatic: Boolean
    get() = hasModifierProperty(PsiModifier.STATIC)

  /**
   * Returns `true` if this declaration has a [PsiModifier.FINAL] modifier.
   */
  val isFinal: Boolean
    get() = hasModifierProperty(PsiModifier.FINAL)

  /**
   * Returns a declaration visibility.
   */
  val visibility: UastVisibility
    get() = UastVisibility[this]

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R = visitor.visitDeclaration(this, data)
}

fun UElement.getContainingDeclaration(): UDeclaration? = withContainingElements.filterIsInstance<UDeclaration>().firstOrNull()

/**
 * A base interface for every [UElement] which have a name identifier. As analogy to [PsiNameIdentifierOwner]
 *
 * Note: [UDeclaration] and [UAnnotation] will extend this interface after all implementations will do
 */
interface UAnchorOwner : UElement {

  val uastAnchor: UIdentifier?

}
