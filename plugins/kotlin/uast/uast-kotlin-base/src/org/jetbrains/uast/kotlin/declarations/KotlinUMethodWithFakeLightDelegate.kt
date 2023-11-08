// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild
import org.jetbrains.uast.kotlin.psi.UastFakeSourceLightAccessor
import org.jetbrains.uast.kotlin.psi.UastFakeSourceLightDefaultAccessor
import org.jetbrains.uast.kotlin.psi.UastFakeSourceLightMethod
import org.jetbrains.uast.kotlin.psi.UastFakeSourceLightMethodBase

sealed class KotlinUMethodWithFakeLightDelegateBase<T : KtDeclaration>(
    private val original: T,
    fakePsi: UastFakeSourceLightMethodBase<T>,
    givenParent: UElement?
) : KotlinUMethod(fakePsi, original, givenParent) {

    private val uAnnotationsPart = UastLazyPart<List<UAnnotation>>()

    override val uAnnotations: List<UAnnotation>
        get() = uAnnotationsPart.getOrBuild {
            original.annotationEntries.map {
                baseResolveProviderService.baseKotlinConverter.convertAnnotation(it, this)
            }
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

internal class KotlinUMethodWithFakeLightDelegateDefaultAccessor(
    original: KtProperty,
    fakePsi: UastFakeSourceLightDefaultAccessor,
    givenParent: UElement?
) : KotlinUMethodWithFakeLightDelegateBase<KtProperty>(original, fakePsi, givenParent) {
    constructor(original: KtProperty, containingLightClass: PsiClass, isSetter: Boolean, givenParent: UElement?)
            : this(original, UastFakeSourceLightDefaultAccessor(original, containingLightClass, isSetter), givenParent)
}

internal class KotlinUMethodWithFakeLightDelegateAccessor(
    original: KtPropertyAccessor,
    fakePsi: UastFakeSourceLightAccessor,
    givenParent: UElement?
) : KotlinUMethodWithFakeLightDelegateBase<KtPropertyAccessor>(original, fakePsi, givenParent) {
    constructor(original: KtPropertyAccessor, containingLightClass: PsiClass, givenParent: UElement?)
            : this(original, UastFakeSourceLightAccessor(original, containingLightClass), givenParent)
}