/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.uast.kotlin.psi

import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.psi.KtFunction

/**
 * An abstraction similar to [UastFakeDescriptorLightMethod], but with the original, deserialized source PSI of [KtFunction].
 *
 * Technically, we can just reuse [UastFakeSourceLightMethod] as-is, which is, however, used as a fake UAST node for methods
 * that are in source, but not converted/supported by light classes (e.g., due to the presence of @JvmSynthetic).
 * To keep the semantics of [UastFakeSourceLightMethod], here we introduce a dummy abstraction as a placeholder.
 */
internal class UastFakeDeserializedLightMethod(
    private val original: KtFunction,
    containingClass: PsiClass,
) : UastFakeSourceLightMethod(original, containingClass) {
    override fun toString(): String = "${this::class.simpleName} of ${original.name}"
}
