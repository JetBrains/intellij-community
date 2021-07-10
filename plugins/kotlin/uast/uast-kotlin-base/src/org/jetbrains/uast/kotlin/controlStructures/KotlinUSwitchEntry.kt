// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.kinds.KotlinSpecialExpressionKinds

class KotlinUSwitchEntry(
    override val sourcePsi: KtWhenEntry,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), USwitchClauseExpressionWithBody {
    override val caseValues by lz {
        sourcePsi.conditions.map {
            baseResolveProviderService.baseKotlinConverter.convertWhenCondition(it, this, DEFAULT_EXPRESSION_TYPES_LIST)
                ?: UastEmptyExpression(null)
        }
    }

    override val body: UExpressionList by lz {
        object : KotlinUExpressionList(
            sourcePsi,
            KotlinSpecialExpressionKinds.WHEN_ENTRY,
            this@KotlinUSwitchEntry,
        ) {
            override fun asRenderString() = buildString {
                appendLine("{")
                expressions.forEach { appendLine(it.asRenderString().withMargin) }
                appendLine("}")
            }
        }.apply KotlinUExpressionList@{
            val exprPsi = this@KotlinUSwitchEntry.sourcePsi.expression
            val userExpressions = when (exprPsi) {
                is KtBlockExpression -> exprPsi.statements.map { baseResolveProviderService.baseKotlinConverter.convertOrEmpty(it, this) }
                else -> listOf(baseResolveProviderService.baseKotlinConverter.convertOrEmpty(exprPsi, this))
            }
            expressions =
                if (userExpressions.isNotEmpty())
                    userExpressions.subList(0, userExpressions.lastIndex) + object : UYieldExpression {
                        override val javaPsi: PsiElement? = null
                        override val sourcePsi: PsiElement? = null
                        override val psi: PsiElement?
                            get() = null
                        override val label: String?
                            get() = null
                        override val uastParent: UElement
                            get() = this@KotlinUExpressionList
                        override val uAnnotations: List<UAnnotation>
                            get() = emptyList()
                        override val expression: UExpression?
                            get() =
                                userExpressions.lastOrNull()?.sourcePsi?.let {
                                    it.safeAs<KtExpression>() ?: it.parent.safeAs<KtExpression>()
                                }?.let {
                                    baseResolveProviderService.baseKotlinConverter.convertExpression(
                                        it, this, DEFAULT_EXPRESSION_TYPES_LIST
                                    )
                                }
                    }
                else emptyList()
        }
    }

    override fun convertParent(): UElement? {
        val result = baseResolveProviderService.baseKotlinConverter.unwrapElements(sourcePsi.parent)?.let { parentUnwrapped ->
            languagePlugin?.convertElementWithParent(parentUnwrapped, null)
        }
        return (result as? KotlinUSwitchExpression)?.body ?: result
    }
}
