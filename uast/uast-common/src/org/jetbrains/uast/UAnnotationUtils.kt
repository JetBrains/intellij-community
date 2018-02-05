// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("UAnnotationUtils")
@file:ApiStatus.Experimental

package org.jetbrains.uast

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.ApiStatus

/*
 * This file contains utility methods to workaround problems with nested annotation in Uast (IDEA-185890).
 * This is an experimental API and it could be dramatically changed or even removed when the bug is fixed.
 */

/**
 * @return an annotation name element (identifier) for annotation entry.
 * Considers not only direct [UAnnotation]s but also nested annotations, which are not always converted to [UAnnotation]
 */
fun getNameElement(uElement: UElement?): PsiElement? =
  when (uElement) {
    is UAnnotation -> uElement.namePsiElement
    is USimpleNameReferenceExpression -> uElement.sourcePsi
    is UCallExpression -> uElement.methodIdentifier?.sourcePsi?.let { PsiTreeUtil.getDeepestFirst(it) }
    else -> null
  }

/**
 * @param identifier an identifier element that occurs in annotation, including annotations nested inside annotations (e.g. as value attribute).
 * Passed element should be convertable to [UIdentifier].
 * @return an [UDeclaration] to which full annotation belongs to or `null` if given argument is not an identifier in annotation.
 */
fun getIdentifierAnnotationOwner(identifier: PsiElement): UDeclaration? =
  getUParentForAnnotationIdentifier(identifier)?.getContainingDeclaration()

/**
 * @param identifier an identifier element that occurs in annotation, including annotations nested inside annotations (e.g. as value attribute).
 * Passed element should be convertable to [UIdentifier].
 * @return an annotation-like owner of passed identifier.
 * Result could be:
 * - an [UAnnotation] if this is a root annotation
 * - an [UCallExpression] if this is a nested annotation
 * - another [UElement] if something strange is going on
 * - `null` if given argument is not an identifier in annotation.
 */
fun getUParentForAnnotationIdentifier(identifier: PsiElement): UElement? {
  val originalParent = getUParentForIdentifier(identifier) ?: return null
  when (originalParent) {
    is UAnnotation ->
      return originalParent

    is UCallExpression ->
      return if (isResolvedToAnnotation(originalParent.classReference)) originalParent else null

    is UReferenceExpression ->
      if (isResolvedToAnnotation(originalParent)) {
        val parentAnyway = originalParent.parentAnyway ?: return null
        val annotationLikeParent = parentAnyway
                                     .withContainingElements
                                     .dropWhile { it is UTypeReferenceExpression || it is UReferenceExpression }
                                     .firstOrNull() ?: return parentAnyway
        if (annotationLikeParent !is UAnnotation && annotationLikeParent.uastParent is UAnnotation)
          return annotationLikeParent.uastParent
        return annotationLikeParent
    }
  }
  return null
}

private fun isResolvedToAnnotation(reference: UReferenceExpression?) = (reference?.resolve() as? PsiClass)?.isAnnotationType == true

private val UElement.parentAnyway
  get() = uastParent ?: generateSequence(sourcePsi?.parent, { it.parent }).mapNotNull { it.toUElement() }.firstOrNull()
