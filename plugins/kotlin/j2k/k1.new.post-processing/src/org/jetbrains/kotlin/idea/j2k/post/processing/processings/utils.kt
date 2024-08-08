// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing.processings

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.nj2k.asExplicitLabel
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtContainerNode
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal fun KtDeclaration.type(): KotlinType? =
    (resolveToDescriptorIfAny() as? CallableDescriptor)?.returnType

internal fun PsiElement.getExplicitLabelComment(): PsiComment? {
    val comment = prevSibling?.safeAs<PsiComment>()
    if (comment?.text?.asExplicitLabel() != null) return comment
    if (parent is KtValueArgument || parent is KtBinaryExpression || parent is KtContainerNode) {
        return parent.getExplicitLabelComment()
    }
    return null
}