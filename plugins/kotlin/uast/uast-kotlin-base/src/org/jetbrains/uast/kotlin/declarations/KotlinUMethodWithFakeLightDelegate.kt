// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.*
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.psi.UastFakeSourceLightMethod

@ApiStatus.Internal
class KotlinUMethodWithFakeLightDelegate(
    val original: KtFunction,
    fakePsi: UastFakeSourceLightMethod,
    givenParent: UElement?
) : KotlinUMethod(fakePsi, original, givenParent) {

    constructor(original: KtFunction, containingLightClass: PsiClass, givenParent: UElement?)
            : this(original, UastFakeSourceLightMethod(original, containingLightClass), givenParent)

    override val uAnnotations: List<UAnnotation> by lz {
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
        other as KotlinUMethodWithFakeLightDelegate
        if (original != other.original) return false
        return true
    }

    override fun hashCode(): Int = original.hashCode()
}
