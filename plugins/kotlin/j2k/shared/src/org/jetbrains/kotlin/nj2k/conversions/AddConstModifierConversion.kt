// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.JKClass.ClassKind
import org.jetbrains.kotlin.nj2k.tree.Mutability.IMMUTABLE
import org.jetbrains.kotlin.nj2k.tree.OtherModifier.CONST
import org.jetbrains.kotlin.nj2k.types.asPrimitiveType
import org.jetbrains.kotlin.nj2k.types.fqName


class AddConstModifierConversion(context: NewJ2kConverterContext) : RecursiveConversion(context) {
    context(KtAnalysisSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKField) return recurse(element)
        if (element.mutability != IMMUTABLE || element.initializer is JKStubExpression) return recurse(element)
        if (element.otherModifierElements.any { it.otherModifier == CONST }) return recurse(element)
        if (KOTLIN_TYPES.none { it == element.type.type.fqName } && element.type.type.asPrimitiveType() == null) return recurse(element)
        val parent = element.parentOfType<JKClass>()
        if (element.parentOfType<JKExpression>() != null || element.parentOfType<JKMethod>() != null) return recurse(element)
        if (parent == null || parent.classKind == ClassKind.OBJECT || parent.classKind == ClassKind.COMPANION) {
            element.otherModifierElements += JKOtherModifierElement(CONST)
        }
        return recurse(element)
    }

    private val KOTLIN_TYPES = setOf(
        "kotlin.Boolean",
        "kotlin.Byte",
        "kotlin.Short",
        "kotlin.String",
        "kotlin.Int",
        "kotlin.Float",
        "kotlin.Long",
        "kotlin.Double"
    )
}

