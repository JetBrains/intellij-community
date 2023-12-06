// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin.psi

import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiType
import com.intellij.psi.impl.light.LightParameter
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ParameterDescriptor
import org.jetbrains.kotlin.resolve.calls.components.isVararg

internal open class UastDescriptorLightParameterBase<T : CallableDescriptor>(
    name: String,
    type: PsiType,
    private val parent: PsiElement,
    private val ktOrigin: T,
    language: Language = parent.language,
    isVararg: Boolean = (ktOrigin as? ParameterDescriptor)?.isVararg == true
) : LightParameter(name, type, parent, language, isVararg) {

    override fun getParent(): PsiElement = parent

    override fun getContainingFile(): PsiFile? = parent.containingFile

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other::class.java != this::class.java) return false
        return ktOrigin == (other as? UastDescriptorLightParameterBase<*>)?.ktOrigin
    }

    override fun hashCode(): Int = ktOrigin.hashCode()
}
