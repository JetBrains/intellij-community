// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.nj2k

import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.j2k.Nullability.NotNull
import org.jetbrains.kotlin.j2k.Nullability.Nullable
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.types.updateNullability

/**
 * The set of possible declaration types is determined by [J2KNullityInferrer],
 * currently: PsiMethod, PsiLocalVariable, PsiParameter, PsiField.
 */
internal data class DeclarationNullabilityInfo(
    val nullableSet: Set<SmartPsiElementPointer<*>>,
    val notNullSet: Set<SmartPsiElementPointer<*>>
)

internal fun JKDeclaration.updateNullability(declarationNullabilityInfo: DeclarationNullabilityInfo) {
    val pointer = psi?.createSmartPointer() ?: return
    val newNullability = when {
        declarationNullabilityInfo.nullableSet.contains(pointer) -> Nullable
        declarationNullabilityInfo.notNullSet.contains(pointer) -> NotNull
        else -> return
    }
    val typeElement = when (this) {
        is JKMethod -> returnType
        is JKLocalVariable -> type
        is JKParameter -> type
        is JKField -> type
        else -> return
    }
    typeElement.type = typeElement.type.updateNullability(newNullability)
}