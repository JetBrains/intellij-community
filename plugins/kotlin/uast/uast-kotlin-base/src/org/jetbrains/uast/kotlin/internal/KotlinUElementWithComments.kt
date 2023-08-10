// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin.internal

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.uast.*

@ApiStatus.Internal
interface KotlinUElementWithComments : UElement {

    override val comments: List<UComment>
        get() {
            val psi = sourcePsi ?: return emptyList()
            val childrenComments = commentsOnPsiElement(psi)
            // Default constructor or synthetic members whose source PSI point to its containing class or object
            if (this !is UClass && psi is KtClassOrObject) {
                // Don't regard class's comments as synthetic members' comments
                return emptyList()
            }
            // Default property accessors
            if (this is UMethod && psi is KtProperty) {
                // Don't regard property's comments as accessor's comments,
                // unless that property won't be materialized (e.g., property in interface)
                val backingField = (uastParent as? UClass)?.fields?.find { it.sourcePsi == psi }
                return if (backingField != null)
                    emptyList()
                else
                    childrenComments
            }
            // Property accessor w/o its own comments
            if (psi is KtPropertyAccessor && childrenComments.isEmpty()) {
                // If the containing property does not have a backing field,
                // comments on the property won't appear on any elements, so we should keep them here.
                val propertyPsi = psi.parent as? KtProperty ?: return childrenComments
                val backingField = (uastParent as? UClass)?.fields?.find { it.sourcePsi == propertyPsi }
                return if (backingField != null)
                    childrenComments
                else
                    commentsOnPsiElement(propertyPsi)
            } // Property accessor w/ its own comments fall through and return those comments.
            if (this !is UExpression &&
                this !is UParameter     // fun (/* prior */ a: Int) <-- /* prior */ is on the level of VALUE_PARAM_LIST
            )
                return childrenComments
            val childrenAndSiblingComments = childrenComments +
                    psi.nearestCommentSibling(forward = true)?.let { listOf(UComment(it, this)) }.orEmpty() +
                    psi.nearestCommentSibling(forward = false)?.let { listOf(UComment(it, this)) }.orEmpty()
            val parent = psi.parent as? KtValueArgument ?: return childrenAndSiblingComments

            return childrenAndSiblingComments +
                    parent.nearestCommentSibling(forward = true)?.let { listOf(UComment(it, this)) }.orEmpty() +
                    parent.nearestCommentSibling(forward = false)?.let { listOf(UComment(it, this)) }.orEmpty()
        }

    private fun commentsOnPsiElement(psi: PsiElement): List<UComment> {
        return psi.allChildren.filterIsInstance<PsiComment>().map { UComment(it, this) }.toList()
    }

    private fun PsiElement.nearestCommentSibling(forward: Boolean): PsiComment? {
        var sibling = if (forward) nextSibling else prevSibling
        while (sibling is PsiWhiteSpace && !sibling.text.contains('\n')) {
            sibling = if (forward) sibling.nextSibling else sibling.prevSibling
        }
        return sibling as? PsiComment
    }

}
