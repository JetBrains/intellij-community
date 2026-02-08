// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UExpressionList
import org.jetbrains.uast.UIdentifier
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild
import org.jetbrains.uast.kotlin.kinds.KotlinSpecialExpressionKinds
import org.jetbrains.uast.withMargin

@ApiStatus.Internal
class KotlinUSwitchExpression(
    override val sourcePsi: KtWhenExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), USwitchExpression, KotlinUElementWithType {

    private val expressionPart = UastLazyPart<UExpression?>()
    private val bodyPart = UastLazyPart<UExpressionList>()

    override val expression: UExpression?
        get() = expressionPart.getOrBuild {
            baseResolveProviderService.baseKotlinConverter.convertOrNull(sourcePsi.subjectExpression, this)
        }

    override val body: UExpressionList
        get() = bodyPart.getOrBuild {
            object : KotlinUExpressionList(
                sourcePsi,
                KotlinSpecialExpressionKinds.WHEN,
                this@KotlinUSwitchExpression,
            ) {
                override fun asRenderString() = expressions.joinToString("\n") { it.asRenderString().withMargin }
            }.apply {
                expressions = this@KotlinUSwitchExpression.sourcePsi.entries.map { KotlinUSwitchEntry(it, this) }
            }
        }

    override fun asRenderString(): String = buildString {
        val expr = expression?.let { "(" + it.asRenderString() + ") " } ?: ""
        appendLine("switch $expr {")
        appendLine(body.asRenderString())
        appendLine("}")
    }

    override val switchIdentifier: UIdentifier
        get() = KotlinUIdentifier(null, this)
}
