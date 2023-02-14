// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJvmModifiersOwner
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.visitor.UastTypedVisitor

/**
 * A [PsiElement] declaration wrapper.
 */
interface UDeclaration : UElement, PsiJvmModifiersOwner, UAnnotated {
  /**
   * Returns the original declaration (which is *always* unwrapped, never a [UDeclaration]).
   */
  @get:ApiStatus.ScheduledForRemoval
  @get:Deprecated("see the base property description")
  @Deprecated("see the base property description", ReplaceWith("javaPsi"))
  override val psi: PsiModifierListOwner

  override fun getOriginalElement(): PsiElement? = sourcePsi?.originalElement

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

interface UDeclarationEx : UDeclaration {
  override val javaPsi: PsiModifierListOwner
}

fun UElement?.getContainingDeclaration(): UDeclaration? = this?.withContainingElements?.drop(1)?.filterIsInstance<UDeclaration>()?.firstOrNull()

fun <T : UElement> UElement?.getContainingDeclaration(cls: Class<out T>): T? {
  val element = this?.withContainingElements?.drop(1)?.filterIsInstance<UDeclaration>()?.firstOrNull()
  return if (element != null && cls.isInstance(element)) {
    @Suppress("UNCHECKED_CAST")
    element as T
  } else {
    null
  }
}

fun UDeclaration?.getAnchorPsi():PsiElement? {
  return this?.uastAnchor?.sourcePsi
}

/**
 * A base interface for every [UElement] which have a name identifier. As analogy to [com.intellij.psi.PsiNameIdentifierOwner]
 */
interface UAnchorOwner : UElement {

  val uastAnchor: UIdentifier?

}
