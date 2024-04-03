// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens.*
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.symbols.JKClassSymbol
import org.jetbrains.kotlin.nj2k.symbols.JKMultiverseClassSymbol
import org.jetbrains.kotlin.nj2k.symbols.JKMultiverseKtClassSymbol
import org.jetbrains.kotlin.nj2k.symbols.JKUniverseClassSymbol
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.OtherModifier.OVERRIDE
import org.jetbrains.kotlin.nj2k.tree.Visibility.*
import org.jetbrains.kotlin.nj2k.types.JKClassType
import org.jetbrains.kotlin.nj2k.types.fqName
import org.jetbrains.kotlin.nj2k.visibility
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierTypeOrDefault


class RedundantModifiersInOverrideConversion(context: NewJ2kConverterContext) : RecursiveConversion(context) {
    context(KtAnalysisSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKMethod || element.otherModifierElements.none { it.otherModifier == OVERRIDE }) return recurse(element)
        if (element.visibilityElement.isRedundant()) return recurse(element) // already know, no need to run checks
        if (element.isRedundantBuiltIn()) {
            element.isRedundantVisibility = true
            return recurse(element)
        }
        val classParent = element.parentOfType<JKClass>()
        if (classParent?.classKind == JKClass.ClassKind.ENUM && element.parentOfType<JKEnumConstant>() != null) {
            classParent.classBody.declarations.forEach { childDeclaration ->
                if (childDeclaration is JKMethod && element.name.value == childDeclaration.name.value && element != childDeclaration) {
                    element.isRedundantVisibility = element.isRedundant(childDeclaration.visibility)
                    return recurse(element)
                }
            }
        }

        val inheritanceInfo = mutableListOf<JKClassSymbol>()
        if (classParent != null) {
            inheritanceInfo.addAll(classParent.inheritance.extends.mapNotNull {
                val type = it.type
                return@mapNotNull if (type is JKClassType) type.classReference else null
            })
            inheritanceInfo.addAll(classParent.inheritance.implements.mapNotNull {
                val type = it.type
                return@mapNotNull if (type is JKClassType) type.classReference else null
            })
        }
        val newExpressionParent = element.parentOfType<JKNewExpression>()
        if (newExpressionParent != null) {
            inheritanceInfo.add(newExpressionParent.classSymbol)
        }

        inheritanceInfo.forEach { classSymbol ->
            when (classSymbol) {
                is JKUniverseClassSymbol -> {
                    val overriddenMethodVisibility =
                        element.findOverriddenMethodVisibility(classSymbol.target, context)
                    if (overriddenMethodVisibility != null) {
                        element.isRedundantVisibility = element.isRedundant(overriddenMethodVisibility)
                        return recurse(element)
                    }
                }

                is JKMultiverseClassSymbol -> {
                    classSymbol.target.allMethods.forEach {
                        if (element.name.value == it.name && element != it) {
                            val overriddenMethodVisibility =
                                it.visibility(context.converter.referenceSearcher, assignNonCodeElements = null)
                            element.isRedundantVisibility = element.isRedundant(overriddenMethodVisibility.visibility)
                            return recurse(element)
                        }
                    }
                }

                is JKMultiverseKtClassSymbol -> {
                    classSymbol.target.body?.children?.forEach {
                        if (it.kotlinFqName?.shortName().toString() == element.name.value) {
                            element.isRedundantVisibility = element.isRedundant(classSymbol.target.visibilityModifierTypeOrDefault())
                            return recurse(element)
                        }
                    }
                }

                else -> {}
            }
        }
        return recurse(element)
    }

    context(KtAnalysisSession)
    private fun JKMethod.findOverriddenMethodVisibility(scope: JKTreeElement, context: NewJ2kConverterContext): Visibility? {
        val implementerMethod = this
        var usage: Visibility? = null
        val searcher = object : RecursiveConversion(context) {
            context(KtAnalysisSession)
            override fun applyToElement(element: JKTreeElement): JKTreeElement {
                if (element is JKMethod) {
                    if (element.name.value == implementerMethod.name.value && element != implementerMethod) {
                        usage = element.visibility
                        return element
                    }
                }
                return recurse(element)
            }
        }
        searcher.run(scope, context)
        return usage
    }

    private fun JKMethod.isRedundant(overrideVisibility: Visibility): Boolean = when (overrideVisibility) {
        visibility -> true
        INTERNAL -> visibility == PUBLIC && (parentOfType<JKClass>()?.visibility == INTERNAL || parentOfType<JKNewExpression>() != null)
        else -> false
    }

    private fun JKMethod.isRedundant(overrideVisibility: KtModifierKeywordToken): Boolean = when (overrideVisibility) {
        PROTECTED_KEYWORD -> visibility == PROTECTED
        PUBLIC_KEYWORD -> visibility == PUBLIC
        INTERNAL_KEYWORD -> visibility == INTERNAL || ((parentOfType<JKClass>()?.visibility == INTERNAL
                || parentOfType<JKNewExpression>() != null) && visibility == PUBLIC)

        else -> false
    }

    private fun JKMethod.isRedundantBuiltIn() = when {
        visibility == PUBLIC && name.value == "toString" && parameters.isEmpty() && returnType.type.fqName == "kotlin.String" -> true
        visibility == PUBLIC && name.value == "equals" && parameters.size == 1 && returnType.type.fqName == "kotlin.Boolean" -> true
        visibility == PUBLIC && name.value == "hashCode" && parameters.isEmpty() && returnType.type.fqName == "kotlin.Int" -> true
        visibility == PROTECTED && name.value == "clone" && parameters.isEmpty() && returnType.type.fqName == "kotlin.Any" -> true
        else -> false
    }
}
