// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin.psi

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiTypes
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.codegen.coroutines.SUSPEND_FUNCTION_COMPLETION_PARAMETER_NAME
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild
import org.jetbrains.uast.kotlin.BaseKotlinUastResolveProviderService

@ApiStatus.Internal
class UastKotlinPsiSuspendContinuationParameter internal constructor(
    private val parent: PsiModifierListOwner,
    suspendFunction: KtFunction,
) : UastKotlinPsiParameterBase<KtFunction>(
    name = SUSPEND_FUNCTION_COMPLETION_PARAMETER_NAME,
    parent = parent,
    ktOrigin = suspendFunction,
    isVarArgs = false,
    ktDefaultValue = null,
    {
        val service = ApplicationManager.getApplication()
            .getService(BaseKotlinUastResolveProviderService::class.java)
        service.getSuspendContinuationType(suspendFunction, parent) ?: PsiTypes.nullType()
    }
) {
    private val annotationsPart = UastLazyPart<Array<PsiAnnotation>>()

    override fun getAnnotations(): Array<PsiAnnotation> {
        return annotationsPart.getOrBuild {
            arrayOf(UastFakeLightNullabilityAnnotation(KaTypeNullability.NON_NULLABLE, this))
        }
    }
}
