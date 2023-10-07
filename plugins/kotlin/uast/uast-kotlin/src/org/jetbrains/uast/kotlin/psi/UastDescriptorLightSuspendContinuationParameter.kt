// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin.psi

import com.intellij.psi.*
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.codegen.coroutines.SUSPEND_FUNCTION_COMPLETION_PARAMETER_NAME
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.getContinuationOfTypeOrAny
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild
import org.jetbrains.uast.kotlin.PsiTypeConversionConfiguration
import org.jetbrains.uast.kotlin.TypeOwnerKind
import org.jetbrains.uast.kotlin.toPsiType

internal class UastDescriptorLightSuspendContinuationParameter(
    parameterType: PsiType,
    parent: PsiElement,
    suspendFunction: FunctionDescriptor,
) : UastDescriptorLightParameterBase<FunctionDescriptor>(
    name = SUSPEND_FUNCTION_COMPLETION_PARAMETER_NAME,
    type = parameterType,
    parent = parent,
    ktOrigin = suspendFunction
) {
    private val annotationsPart = UastLazyPart<Array<PsiAnnotation>>()

    private val _annotations: Array<PsiAnnotation>
        get() = annotationsPart.getOrBuild {
            arrayOf(UastFakeLightNullabilityAnnotation(KtTypeNullability.NON_NULLABLE, this))
        }

    override fun getAnnotations(): Array<PsiAnnotation> {
        return _annotations
    }

    override fun hasAnnotation(fqn: String): Boolean {
        return _annotations.find { it.hasQualifiedName(fqn) } != null
    }

    companion object {
        fun create(
            parent: PsiModifierListOwner,
            suspendFunction: FunctionDescriptor,
            context: KtElement,
        ): PsiParameter {
            val moduleDescriptor = DescriptorUtils.getContainingModule(suspendFunction)
            val parameterType = suspendFunction.returnType?.let { returnType ->
                val continuationType = moduleDescriptor.getContinuationOfTypeOrAny(returnType)
                continuationType.toPsiType(
                    parent,
                    context,
                    PsiTypeConversionConfiguration(TypeOwnerKind.DECLARATION)
                )
            } ?: PsiTypes.nullType()
            return UastDescriptorLightSuspendContinuationParameter(parameterType, parent, suspendFunction)
        }
    }
}
