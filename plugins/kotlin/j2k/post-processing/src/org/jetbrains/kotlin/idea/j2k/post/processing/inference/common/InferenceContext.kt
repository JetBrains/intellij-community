// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.j2k.post.processing.inference.common

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtTypeElement

data class InferenceContext(
    val elements: List<KtElement>,
    val typeVariables: List<TypeVariable>,
    val typeElementToTypeVariable: Map<KtTypeElement, TypeVariable>,
    val declarationToTypeVariable: Map<KtNamedDeclaration, TypeVariable>,
    val declarationDescriptorToTypeVariable: Map<DeclarationDescriptor, TypeVariable>,
    val superTypesSubstitutions: Map<ClassDescriptor, SuperTypesSubstitutor>

) {
    fun isInConversionScope(childCandidate: PsiElement) = when (childCandidate) {
        is KtElement -> elements.any { element ->
            element.containingKtFile == childCandidate.containingKtFile
                    && element.textRange.contains(childCandidate.textRange)
        }

        else -> false
    }
}