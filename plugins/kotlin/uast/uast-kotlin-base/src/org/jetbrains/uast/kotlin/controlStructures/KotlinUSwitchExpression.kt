// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.*
import org.jetbrains.uast.kotlin.kinds.KotlinSpecialExpressionKinds

class KotlinUSwitchExpression(
    override val sourcePsi: KtWhenExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), USwitchExpression, KotlinUElementWithType {
    override val expression by lz {
        baseResolveProviderService.baseKotlinConverter.convertOrNull(sourcePsi.subjectExpression, this)
    }

    override val body: UExpressionList by lz {
        object : KotlinUExpressionList(
            sourcePsi,
            KotlinSpecialExpressionKinds.WHEN,
            this@KotlinUSwitchExpression,
            baseResolveProviderService
        ) {
            override fun asRenderString() = expressions.joinToString("\n") { it.asRenderString().withMargin }
        }.apply {
            expressions = this@KotlinUSwitchExpression.sourcePsi.entries.map { KotlinUSwitchEntry(it, this) }
        }
    }

    override fun asRenderString() = buildString {
        val expr = expression?.let { "(" + it.asRenderString() + ") " } ?: ""
        appendLine("switch $expr {")
        appendLine(body.asRenderString())
        appendLine("}")
    }

    override val switchIdentifier: UIdentifier
        get() = KotlinUIdentifier(null, this)
}
