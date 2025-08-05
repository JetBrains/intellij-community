// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.Nullability.NotNull
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.isObjectOrCompanionObject
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.Mutability.IMMUTABLE
import org.jetbrains.kotlin.nj2k.tree.OtherModifier.CONST
import org.jetbrains.kotlin.nj2k.types.asPrimitiveType
import org.jetbrains.kotlin.nj2k.types.fqName

class AddConstModifierConversion(context: ConverterContext) : RecursiveConversion(context) {
    context(_: KaSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKField) return recurse(element)
        if (element.mutability != IMMUTABLE) return recurse(element)
        if (element.initializer !is JKLiteralExpression) return recurse(element)
        if (element.otherModifierElements.any { it.otherModifier == CONST }) return recurse(element)
        if (element.type.type.nullability != NotNull) return recurse(element)
        if (element.type.type.fqName !in KOTLIN_TYPES && element.type.type.asPrimitiveType() == null) return recurse(element)
        if (element.parentOfType<JKExpression>() != null || element.parentOfType<JKMethod>() != null) return recurse(element)

        val containingDeclaration = element.parentOfType<JKClass>()
        if (containingDeclaration == null || containingDeclaration.isObjectOrCompanionObject) {
            element.otherModifierElements += JKOtherModifierElement(CONST)
        }
        return recurse(element)
    }
}

private val KOTLIN_TYPES: Set<String> = setOf(
    "kotlin.Boolean",
    "kotlin.Byte",
    "kotlin.Short",
    "kotlin.String",
    "kotlin.Int",
    "kotlin.Float",
    "kotlin.Long",
    "kotlin.Double"
)
