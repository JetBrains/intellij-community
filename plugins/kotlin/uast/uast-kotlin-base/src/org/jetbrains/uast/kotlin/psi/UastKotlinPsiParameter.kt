// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin.psi

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.asJava.elements.KtLightAnnotationForSourceEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.BaseKotlinUastResolveProviderService

/**
 * Kotlin PSI parameter that can be used when the parameter doesn't exist in light classes.
 */
@ApiStatus.Internal
class UastKotlinPsiParameter internal constructor(
    name: String,
    parent: PsiElement,
    isVarArgs: Boolean,
    ktDefaultValue: KtExpression?,
    val ktParameter: KtParameter
) : UastKotlinPsiParameterBase<KtParameter>(name, parent, ktParameter, isVarArgs, ktDefaultValue, {
    val containing = (if (parent is PsiParameterList) parent.parent else parent) as? PsiModifierListOwner
    val baseResolveService = ApplicationManager.getApplication()
        .getService(BaseKotlinUastResolveProviderService::class.java)
    val type = baseResolveService.getType(ktParameter, containing, isForFake = true) ?: UastErrorType
    type.toEllipsisTypeIfNeeded(isVarArgs)
}) {
    private val annotationsPart = UastLazyPart<Array<PsiAnnotation>>()

    override fun getAnnotations(): Array<PsiAnnotation> {
        return annotationsPart.getOrBuild {
            val annotations = mutableListOf<PsiAnnotation>()
            val baseResolveService = ApplicationManager.getApplication()
                .getService(BaseKotlinUastResolveProviderService::class.java)
            val hasInheritedGenericType = baseResolveService.hasInheritedGenericType(ktParameter)
            if (!hasInheritedGenericType) {
                val nullability = baseResolveService.nullability(ktParameter)
                if (nullability != null && nullability != KaTypeNullability.UNKNOWN) {
                    annotations.add(UastFakeLightNullabilityAnnotation(nullability, this))
                }
            }
            ktParameter.annotationEntries.mapTo(annotations) { entry ->
                KtLightAnnotationForSourceEntry(
                    name = entry.shortName?.identifier,
                    lazyQualifiedName = { baseResolveService.qualifiedAnnotationName(entry) },
                    kotlinOrigin = entry,
                    parent = ktParameter,
                )
            }

            annotations.toTypedArray()
        }
    }

    companion object {
        internal fun create(
            parameter: KtParameter,
            parent: PsiElement,
            containingElement: UElement,
            index: Int
        ): PsiParameter {
            val psiParent = containingElement.getParentOfType<UDeclaration>()?.javaPsi ?: parent
            return UastKotlinPsiParameter(
                parameter.name ?: "p$index",
                psiParent,
                parameter.isVarArg,
                parameter.defaultValue,
                parameter
            )
        }
    }
}
