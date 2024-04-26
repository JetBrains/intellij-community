// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin.psi

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild

@ApiStatus.Internal
class UastKotlinPsiSetterParameter<T : KtCallableDeclaration> internal constructor(
    parameterType: PsiType,
    parent: PsiElement,
    ktOrigin: T,
    private val nullability: KtTypeNullability?,
) : UastKotlinPsiParameterBase<T>(
    name = SpecialNames.IMPLICIT_SET_PARAMETER.asString(),
    type = parameterType,
    parent = parent,
    ktOrigin = ktOrigin,
    language = ktOrigin.language,
    isVarArgs = false,
    ktDefaultValue = null
) {

    private val annotationsPart = UastLazyPart<Array<PsiAnnotation>>()

    override val _annotations: Array<PsiAnnotation>
        get() = annotationsPart.getOrBuild {
            if (nullability != null) {
                arrayOf(UastFakeLightNullabilityAnnotation(nullability, this))
            } else {
                arrayOf()
            }
        }
}
