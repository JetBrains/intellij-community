// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("UAnnotationUtils")

package org.jetbrains.uast

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/*
 * This file contains utility methods to workaround problems with nested annotation in Uast (IDEA-185890).
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
 * - another [UElement] if something strange is going on (invalid code for instance)
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

/**
 * @param uElement an element that occurs in annotation
 * @return the annotation in which this element occurs and a corresponding parameter name if available
 */
fun getContainingUAnnotationEntry(uElement: UElement?): Pair<UAnnotation, String?>? {

  fun tryConvertToEntry(uElement: UElement, parent: UElement, name: String?): Pair<UAnnotation, String?>? {
    if (uElement !is UExpression) return null
    val uAnnotation = parent.sourcePsi.toUElementOfType<UAnnotation>() ?: return null
    val argumentSourcePsi = wrapULiteral(uElement).sourcePsi
    return uAnnotation to (name ?: uAnnotation.attributeValues.find { wrapULiteral(it.expression).sourcePsi === argumentSourcePsi }?.name)
  }

  tailrec fun retrievePsiAnnotationEntry(uElement: UElement?, name: String?): Pair<UAnnotation, String?>? {
    if (uElement == null) return null
    val parent = uElement.uastParent ?: return null
    return when (parent) {
      is UAnnotation -> parent to name
      is UReferenceExpression -> tryConvertToEntry(uElement, parent, name)
      is UCallExpression ->
        if (parent.kind == UastCallKind.NESTED_ARRAY_INITIALIZER)
          retrievePsiAnnotationEntry(parent, null)
        else
          tryConvertToEntry(uElement, parent, name)
      is UPolyadicExpression -> retrievePsiAnnotationEntry(parent, null)
      is UNamedExpression -> retrievePsiAnnotationEntry(parent, parent.name)
      else -> null
    }
  }

  return retrievePsiAnnotationEntry(uElement, null)
}

fun getContainingAnnotationEntry(uElement: UElement?): Pair<PsiAnnotation, String?>? {
  val (uAnnotation, name) = getContainingUAnnotationEntry(uElement) ?: return null
  val psiAnnotation = uAnnotation.javaPsi ?: return null
  return psiAnnotation to name
}

private fun isResolvedToAnnotation(reference: UReferenceExpression?) = (reference?.resolve() as? PsiClass)?.isAnnotationType == true

private val UElement.parentAnyway
  get() = uastParent ?: generateSequence(sourcePsi?.parent, { it.parent }).mapNotNull { it.toUElement() }.firstOrNull()
