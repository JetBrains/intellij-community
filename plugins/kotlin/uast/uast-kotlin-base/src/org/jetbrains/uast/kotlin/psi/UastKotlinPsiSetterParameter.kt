// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin.psi

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.uast.UastErrorType
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild
import org.jetbrains.uast.kotlin.BaseKotlinUastResolveProviderService

@ApiStatus.Internal
class UastKotlinPsiSetterParameter<T : KtCallableDeclaration> internal constructor(
    parent: PsiElement,
    ktOrigin: T,
    private val nullability: KaTypeNullability?,
) : UastKotlinPsiParameterBase<T>(
    name = SpecialNames.IMPLICIT_SET_PARAMETER.asString(),
    parent = parent,
    ktOrigin = ktOrigin,
    isVarArgs = false,
    ktDefaultValue = null,
    typeProvider = {
        val baseResolveService = ApplicationManager.getApplication()
            .getService(BaseKotlinUastResolveProviderService::class.java)
        baseResolveService.getType(ktOrigin, parent.parent as PsiModifierListOwner, isForFake = true) ?: UastErrorType
    }
) {
    private val annotationsPart = UastLazyPart<Array<PsiAnnotation>>()

    override fun getAnnotations(): Array<PsiAnnotation> {
        return annotationsPart.getOrBuild {
            if (nullability != null) {
                arrayOf(UastFakeLightNullabilityAnnotation(nullability, this))
            } else {
                arrayOf()
            }
        }
    }
}
