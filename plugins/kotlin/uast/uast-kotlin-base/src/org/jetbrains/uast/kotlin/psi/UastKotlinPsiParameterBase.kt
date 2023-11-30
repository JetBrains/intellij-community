// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin.psi

import com.intellij.lang.Language
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiType
import com.intellij.psi.impl.light.LightParameter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression

@ApiStatus.Internal
open class UastKotlinPsiParameterBase<T : KtElement>(
    name: String,
    type: PsiType,
    private val parent: PsiElement,
    val ktOrigin: T,
    language: Language = ktOrigin.language,
    isVarArgs: Boolean = false,
    val ktDefaultValue: KtExpression? = null,
) : LightParameter(name, type, parent, language, isVarArgs) {

    protected open val _annotations: Array<PsiAnnotation> = arrayOf()

    override fun getAnnotations(): Array<PsiAnnotation> {
        return _annotations
    }

    override fun hasAnnotation(fqn: String): Boolean {
        return _annotations.find { it.hasQualifiedName(fqn) } != null
    }

    override fun getParent(): PsiElement = parent

    override fun getContainingFile(): PsiFile? = ktOrigin.containingFile

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other::class.java != this::class.java) return false
        return ktOrigin == (other as? UastKotlinPsiParameterBase<*>)?.ktOrigin
    }

    override fun hashCode(): Int = ktOrigin.hashCode()
}
