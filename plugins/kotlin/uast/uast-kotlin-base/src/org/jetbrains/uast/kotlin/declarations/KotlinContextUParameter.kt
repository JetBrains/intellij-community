// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.kotlin.declarations

import com.intellij.psi.PsiParameter
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.uast.UElement
import org.jetbrains.uast.kotlin.KotlinUParameter

internal class KotlinContextUParameter(
    psi: PsiParameter, sourcePsi: KtElement?, givenParent: UElement?
) : KotlinUParameter(psi, sourcePsi, givenParent) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false
        other as KotlinContextUParameter
        // context parameters for a property's getter and setter have the same PSI, but different UAST parents
        if (uastParent != other.uastParent) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + uastParent.hashCode()
        return result
    }
}
