// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.utils.SmartSet
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild
import org.jetbrains.uast.kotlin.psi.UastFakeSourceLightAccessor
import org.jetbrains.uast.kotlin.psi.UastFakeSourceLightDefaultAccessor
import org.jetbrains.uast.kotlin.psi.UastFakeSourceLightDefaultAccessorForConstructorParameter
import org.jetbrains.uast.kotlin.psi.UastFakeSourceLightMethod
import org.jetbrains.uast.kotlin.psi.UastFakeSourceLightMethodBase

sealed class KotlinUMethodWithFakeLightDelegateBase<T : KtDeclaration>(
    protected val original: T,
    fakePsi: UastFakeSourceLightMethodBase<T>,
    givenParent: UElement?
) : KotlinUMethod(fakePsi, original, givenParent) {

    private val uAnnotationsPart = UastLazyPart<List<UAnnotation>>()

    override val uAnnotations: List<UAnnotation>
        get() = uAnnotationsPart.getOrBuild {
            val annotations = SmartSet.create<UAnnotation>()
            computeAnnotations(annotations)
            annotations.toList()
        }

    protected open fun computeAnnotations(annotations: SmartSet<UAnnotation>) {
        original.annotationEntries.mapTo(annotations) {
            baseResolveProviderService.baseKotlinConverter.convertAnnotation(it, this)
        }
    }

    override fun getAnnotations(): Array<PsiAnnotation> {
        return javaPsi.annotations
    }

    override fun getAnnotation(fqn: @NonNls String): PsiAnnotation? {
        return javaPsi.getAnnotation(fqn)
    }

    override fun hasAnnotation(fqName: String): Boolean {
        return javaPsi.hasAnnotation(fqName)
    }

    override fun getTextRange(): TextRange {
        return original.textRange
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as KotlinUMethodWithFakeLightDelegateBase<*>
        return original == other.original
    }

    override fun hashCode(): Int = original.hashCode()
}

internal class KotlinUMethodWithFakeLightDelegateMethod(
    original: KtFunction,
    fakePsi: UastFakeSourceLightMethod,
    givenParent: UElement?
) : KotlinUMethodWithFakeLightDelegateBase<KtFunction>(original, fakePsi, givenParent) {
    constructor(original: KtFunction, containingLightClass: PsiClass, givenParent: UElement?)
            : this(original, UastFakeSourceLightMethod(original, containingLightClass), givenParent)
}

private interface KotlinUMethodWithFakeLightDelegateAccessorBase {
    fun computeAnnotationsFromProperty(
        annotations: SmartSet<UAnnotation>,
        accessor: KotlinUMethod,
        property: KtProperty,
        useSiteTarget: AnnotationUseSiteTarget,
    ) {
        property.annotationEntries
            .filter { it.useSiteTarget?.getAnnotationUseSiteTarget() == useSiteTarget }
            .mapTo(annotations) { entry ->
                accessor.baseResolveProviderService.baseKotlinConverter.convertAnnotation(entry, accessor)
            }
    }
}

internal class KotlinUMethodWithFakeLightDelegateDefaultAccessorForConstructorProperty(
    original: KtParameter,
    fakePsi: UastFakeSourceLightDefaultAccessorForConstructorParameter,
    givenParent: UElement?
) : KotlinUMethodWithFakeLightDelegateBase<KtParameter>(original, fakePsi, givenParent)

internal class KotlinUMethodWithFakeLightDelegateDefaultAccessor(
    original: KtProperty,
    private val fakePsi: UastFakeSourceLightDefaultAccessor,
    givenParent: UElement?
) : KotlinUMethodWithFakeLightDelegateBase<KtProperty>(original, fakePsi, givenParent),
    KotlinUMethodWithFakeLightDelegateAccessorBase {
    override fun computeAnnotations(annotations: SmartSet<UAnnotation>) {
        // Annotations on property accessor
        super.computeAnnotations(annotations)
        // Annotations on property, along with use-site target
        val useSiteTarget = if (fakePsi.isSetter) AnnotationUseSiteTarget.PROPERTY_SETTER else AnnotationUseSiteTarget.PROPERTY_GETTER
        computeAnnotationsFromProperty(annotations, this, original, useSiteTarget)
    }
}

internal class KotlinUMethodWithFakeLightDelegateAccessor(
    original: KtPropertyAccessor,
    fakePsi: UastFakeSourceLightAccessor,
    givenParent: UElement?
) : KotlinUMethodWithFakeLightDelegateBase<KtPropertyAccessor>(original, fakePsi, givenParent),
    KotlinUMethodWithFakeLightDelegateAccessorBase {
    constructor(original: KtPropertyAccessor, containingLightClass: PsiClass, givenParent: UElement?)
            : this(original, UastFakeSourceLightAccessor(original, containingLightClass), givenParent)

    override fun computeAnnotations(annotations: SmartSet<UAnnotation>) {
        // Annotations on property accessor
        super.computeAnnotations(annotations)
        // Annotations on property, along with use-site target
        val useSiteTarget = if (original.isSetter) AnnotationUseSiteTarget.PROPERTY_SETTER else AnnotationUseSiteTarget.PROPERTY_GETTER
        computeAnnotationsFromProperty(annotations, this, original.property, useSiteTarget)
    }
}