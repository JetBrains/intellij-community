// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("UAnnotationUtils")
@file:ApiStatus.Experimental

package org.jetbrains.uast

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

/*
 * This file contains utility methods to workaround problems with nested annotation in Uast (IDEA-185890).
 * This is an experimental API and it could be dramatically changed or even removed when the bug is fixed.
 */

/**
 * @return an annotation name element (identifier) for annotation entry.
 * Considers not only direct [UAnnotation]s but also annotations in annotations which are not always converted to [UAnnotation]
 */
fun getNameElement(uElement: UElement?): PsiElement? =
  when (uElement) {
    is UAnnotation -> uElement.namePsiElement
    is USimpleNameReferenceExpression -> uElement.sourcePsi
    is UCallExpression -> uElement.methodIdentifier?.sourcePsi?.firstChild
    else -> null
  }

/**
 * @param identifier an identifier element that occurs in annotation, including annotations nested inside annotations (e.g. as value attribute).
 * Passed element should be convertable to [UIdentifier].
 * @return an [UDeclaration] to which full annotation belongs to or `null` if given argument is not an identifier in annotation.
 */
fun getIdentifierAnnotationOwner(identifier: PsiElement): UDeclaration? {
  val originalParent = getUParentForIdentifier(identifier) ?: return null
  when (originalParent) {
    is UAnnotation -> return originalParent.getContainingDeclaration()
    is UReferenceExpression -> {
      val resolve = originalParent.resolve()
      if (resolve is PsiClass && resolve.isAnnotationType)
        return originalParent.parentAnyway?.getContainingDeclaration()
    }
  }
  return null
}

private val UElement.parentAnyway
  get() = uastParent ?: generateSequence(sourcePsi?.parent, { it.parent }).mapNotNull { it.toUElement() }.firstOrNull()
