// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import org.jetbrains.kotlin.j2k.Nullability

interface J2KNullabilityInferenceExtension {
    companion object {
        val EP_NAME: ExtensionPointName<J2KNullabilityInferenceExtension> =
            ExtensionPointName.create("org.jetbrains.kotlin.j2kNullabilityInferenceExtension")

        @JvmStatic
        fun getNullability(element: PsiParameter): Nullability? = EP_NAME.computeSafeIfAny { it.calculateNullability(element) }

        @JvmStatic
        fun getNullability(element: PsiMethod): Nullability? = EP_NAME.computeSafeIfAny { it.calculateNullability(element) }

        @JvmStatic
        fun getNullability(element: PsiField): Nullability? = EP_NAME.computeSafeIfAny { it.calculateNullability(element) }
    }

    fun calculateNullability(element: PsiParameter): Nullability?

    fun calculateNullability(element: PsiMethod): Nullability?

    fun calculateNullability(element: PsiField): Nullability?
}
