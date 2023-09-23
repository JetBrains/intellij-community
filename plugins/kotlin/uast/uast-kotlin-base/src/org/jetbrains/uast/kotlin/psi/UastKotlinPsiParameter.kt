// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin.psi

import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.asJava.elements.KtLightAnnotationForSourceEntry
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.utils.SmartList
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UastErrorType
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.kotlin.BaseKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.lz

@ApiStatus.Internal
class UastKotlinPsiParameter internal constructor(
    private val baseResolveProviderService: BaseKotlinUastResolveProviderService,
    name: String,
    type: PsiType,
    parent: PsiElement,
    language: Language,
    isVarArgs: Boolean,
    ktDefaultValue: KtExpression?,
    ktParameter: KtParameter
) : UastKotlinPsiParameterBase<KtParameter>(name, type, parent, ktParameter, language, isVarArgs, ktDefaultValue) {

    private val _annotations: Array<PsiAnnotation> by lz {
        val annotations = SmartList<PsiAnnotation>()

        val nullability = baseResolveProviderService.nullability(ktParameter)
        if (nullability != null && nullability != KtTypeNullability.UNKNOWN) {
            annotations.add(
                UastFakeLightNullabilityAnnotation(nullability, this)
            )
        }

        ktParameter.annotationEntries.mapTo(annotations) { entry ->
            KtLightAnnotationForSourceEntry(
                name = entry.shortName?.identifier,
                lazyQualifiedName = { baseResolveProviderService.qualifiedAnnotationName(entry) },
                kotlinOrigin = entry,
                parent = ktParameter,
            )
        }

        annotations.toTypedArray()
    }

    override fun getAnnotations(): Array<PsiAnnotation> {
        return _annotations
    }

    override fun hasAnnotation(fqn: String): Boolean {
        return _annotations.find { it.hasQualifiedName(fqn) } != null
    }

    companion object {
        fun create(
            parameter: KtParameter,
            parent: PsiElement,
            containingElement: UElement,
            index: Int
        ): PsiParameter {
            val service = ApplicationManager.getApplication().getService(BaseKotlinUastResolveProviderService::class.java)
            val psiParent = containingElement.getParentOfType<UDeclaration>()?.javaPsi ?: parent
            return UastKotlinPsiParameter(
                service,
                parameter.name ?: "p$index",
                service.getType(parameter, containingElement) ?: UastErrorType,
                psiParent,
                KotlinLanguage.INSTANCE,
                parameter.isVarArg,
                parameter.defaultValue,
                parameter
            )
        }
    }

    val ktParameter: KtParameter get() = ktOrigin

}
