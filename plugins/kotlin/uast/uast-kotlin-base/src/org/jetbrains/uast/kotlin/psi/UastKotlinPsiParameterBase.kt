// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiType
import com.intellij.psi.impl.light.LightParameter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild

@ApiStatus.Internal
open class UastKotlinPsiParameterBase<T : KtElement>(
    name: String,
    private val parent: PsiElement,
    val ktOrigin: T,
    isVarArgs: Boolean = false,
    val ktDefaultValue: KtExpression? = null,
    typeProvider: () -> PsiType,
) : LightParameter(name, parent, typeProvider, KotlinLanguage.INSTANCE, isVarArgs) {
    protected val typePart = UastLazyPart<PsiType>()

    override fun getType(): PsiType {
        return typePart.getOrBuild { super.type }
    }

    override fun getParent(): PsiElement = parent

    override fun getContainingFile(): PsiFile? = ktOrigin.containingFile

    override fun hasAnnotation(fqn: String): Boolean {
        return annotations.any { annotation -> annotation.qualifiedName == fqn }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other::class.java != this::class.java) return false
        return ktOrigin == (other as? UastKotlinPsiParameterBase<*>)?.ktOrigin
    }

    override fun hashCode(): Int = ktOrigin.hashCode()
}
