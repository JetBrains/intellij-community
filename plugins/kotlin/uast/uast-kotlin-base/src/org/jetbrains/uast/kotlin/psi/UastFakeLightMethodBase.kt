// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin.psi

import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightMethodBuilder
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.utils.SmartSet
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild
import org.jetbrains.uast.kotlin.BaseKotlinUastResolveProviderService

@ApiStatus.Internal
abstract class UastFakeLightMethodBase(
    manager: PsiManager,
    language: Language,
    name: String,
    parameterList: PsiParameterList,
    modifierList: PsiModifierList,
    containingClass: PsiClass,
) : LightMethodBuilder(
    manager,
    language,
    name,
    parameterList,
    modifierList,
) {
    init {
        this.containingClass = containingClass
    }

    private val annotationsPart = UastLazyPart<Array<PsiAnnotation>>()

    protected val baseResolveProviderService: BaseKotlinUastResolveProviderService
        get() {
            return ApplicationManager.getApplication().getService(BaseKotlinUastResolveProviderService::class.java)
                ?: error("${BaseKotlinUastResolveProviderService::class.java.name} is not available for ${this::class.simpleName}")
        }

    protected abstract fun isSuspendFunction(): Boolean
    protected abstract fun isUnitFunction(): Boolean
    protected abstract fun computeNullability(): KaTypeNullability?
    protected abstract fun computeAnnotations(annotations: SmartSet<PsiAnnotation>)

    private val _annotations: Array<PsiAnnotation>
        get() = annotationsPart.getOrBuild {
            val annotations = SmartSet.create<PsiAnnotation>()

            // Do not annotate Unit function (except for suspend function)
            if (!isUnitFunction() || isSuspendFunction()) {
                val nullability = computeNullability()
                if (nullability != null && nullability != KaTypeNullability.UNKNOWN) {
                    annotations.add(
                        UastFakeLightNullabilityAnnotation(nullability, this)
                    )
                }
            }

            computeAnnotations(annotations)

            if (annotations.isNotEmpty()) annotations.toTypedArray() else PsiAnnotation.EMPTY_ARRAY
        }

    override fun getAnnotations(): Array<PsiAnnotation> {
        return _annotations
    }

    override fun getAnnotation(fqn: String): PsiAnnotation? {
        return _annotations.find { it.hasQualifiedName(fqn) }
    }

    override fun hasAnnotation(fqn: String): Boolean {
        return getAnnotation(fqn) != null
    }

    override fun isDeprecated(): Boolean {
        return hasAnnotation(StandardClassIds.Annotations.Deprecated.asFqNameString()) ||
                hasAnnotation(CommonClassNames.JAVA_LANG_DEPRECATED) ||
                super.isDeprecated()
    }

    override fun getParent(): PsiElement? = containingClass

    override fun toString(): String = "${this::class.simpleName} of $name"
}