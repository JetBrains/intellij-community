// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.uast.*
import org.jetbrains.uast.expressions.UInjectionHost
import org.jetbrains.uast.kotlin.expressions.FirKotlinUArrayAccessExpression
import org.jetbrains.uast.kotlin.expressions.FirKotlinUBinaryExpression
import org.jetbrains.uast.kotlin.expressions.FirKotlinUSimpleReferenceExpression
import org.jetbrains.uast.kotlin.internal.firKotlinUastPlugin
import org.jetbrains.uast.kotlin.psi.*

internal object FirKotlinConverter : BaseKotlinConverter {

    override val languagePlugin: UastLanguagePlugin
        get() = firKotlinUastPlugin

    var forceUInjectionHost = Registry.`is`("kotlin.fir.uast.force.uinjectionhost", false)
        @TestOnly
        set(value) {
            field = value
        }

    override fun forceUInjectionHost(): Boolean {
        return forceUInjectionHost
    }

    override fun convertExpression(
        expression: KtExpression,
        givenParent: UElement?,
        requiredTypes: Array<out Class<out UElement>>
    ): UExpression? {

        fun <P : PsiElement> build(ctor: (P, UElement?) -> UExpression): () -> UExpression? {
            return {
                @Suppress("UNCHECKED_CAST")
                ctor(expression as P, givenParent)
            }
        }

        return with(requiredTypes) {
            when (expression) {
                is KtVariableDeclaration -> expr<UDeclarationsExpression>(build(::convertVariablesDeclaration))
                is KtDestructuringDeclaration -> expr<UDeclarationsExpression> {
                    val declarationsExpression = KotlinUDestructuringDeclarationExpression(givenParent, expression)
                    declarationsExpression.apply {
                        val tempAssignment = KotlinULocalVariable(
                            UastKotlinPsiVariable.create(expression, declarationsExpression),
                            expression,
                            declarationsExpression
                        )
                        val destructuringAssignments = expression.entries.mapIndexed { i, entry ->
                            val psiFactory = KtPsiFactory(expression.project)
                            val initializer = psiFactory.createAnalyzableExpression(
                                "${tempAssignment.name}.component${i + 1}()",
                                expression.containingFile
                            )
                            initializer.destructuringDeclarationInitializer = true
                            KotlinULocalVariable(
                                UastKotlinPsiVariable.create(entry, tempAssignment.javaPsi, declarationsExpression, initializer),
                                entry,
                                declarationsExpression
                            )
                        }
                        declarations = listOf(tempAssignment) + destructuringAssignments
                    }
                }

                is KtStringTemplateExpression -> {
                    when {
                        forceUInjectionHost || requiredTypes.contains(UInjectionHost::class.java) -> {
                            expr<UInjectionHost> { KotlinStringTemplateUPolyadicExpression(expression, givenParent) }
                        }
                        expression.entries.isEmpty() -> {
                            expr<ULiteralExpression> { KotlinStringULiteralExpression(expression, givenParent, "") }
                        }
                        expression.entries.size == 1 -> {
                            convertStringTemplateEntry(expression.entries[0], givenParent, requiredTypes)
                        }
                        else -> {
                            expr<KotlinStringTemplateUPolyadicExpression> {
                                KotlinStringTemplateUPolyadicExpression(expression, givenParent)
                            }
                        }
                    }
                }
                is KtCollectionLiteralExpression -> expr<UCallExpression>(build(::KotlinUCollectionLiteralExpression))
                is KtConstantExpression -> expr<ULiteralExpression>(build(::KotlinULiteralExpression))

                is KtLabeledExpression -> expr<ULabeledExpression>(build(::KotlinULabeledExpression))
                is KtParenthesizedExpression -> expr<UParenthesizedExpression>(build(::KotlinUParenthesizedExpression))

                is KtBlockExpression -> expr<UBlockExpression> {
                    if (expression.parent is KtFunctionLiteral &&
                        expression.parent.parent is KtLambdaExpression &&
                        givenParent !is KotlinULambdaExpression
                    ) {
                        KotlinULambdaExpression(expression.parent.parent as KtLambdaExpression, givenParent).body
                    } else
                        KotlinUBlockExpression(expression, givenParent)
                }
                is KtReturnExpression -> expr<UReturnExpression>(build(::KotlinUReturnExpression))
                is KtThrowExpression -> expr<UThrowExpression>(build(::KotlinUThrowExpression))
                is KtTryExpression -> expr<UTryExpression>(build(::KotlinUTryExpression))

                is KtBreakExpression -> expr<UBreakExpression>(build(::KotlinUBreakExpression))
                is KtContinueExpression -> expr<UContinueExpression>(build(::KotlinUContinueExpression))
                is KtDoWhileExpression -> expr<UDoWhileExpression>(build(::KotlinUDoWhileExpression))
                is KtWhileExpression -> expr<UWhileExpression>(build(::KotlinUWhileExpression))
                is KtForExpression -> expr<UForEachExpression>(build(::KotlinUForEachExpression))

                is KtWhenExpression -> expr<USwitchExpression>(build(::KotlinUSwitchExpression))
                is KtIfExpression -> expr<UIfExpression>(build(::KotlinUIfExpression))

                is KtBinaryExpressionWithTypeRHS -> expr<UBinaryExpressionWithType>(build(::KotlinUBinaryExpressionWithType))
                is KtIsExpression -> expr<UBinaryExpressionWithType>(build(::KotlinUTypeCheckExpression))

                is KtArrayAccessExpression -> expr<UArrayAccessExpression>(build(::FirKotlinUArrayAccessExpression))

                is KtThisExpression -> expr<UThisExpression>(build(::KotlinUThisExpression))
                is KtSuperExpression -> expr<USuperExpression>(build(::KotlinUSuperExpression))
                is KtCallableReferenceExpression -> expr<UCallableReferenceExpression>(build(::KotlinUCallableReferenceExpression))
                is KtClassLiteralExpression -> expr<UClassLiteralExpression>(build(::KotlinUClassLiteralExpression))
                is KtObjectLiteralExpression -> expr<UObjectLiteralExpression>(build(::KotlinUObjectLiteralExpression))
                is KtDotQualifiedExpression -> expr<UQualifiedReferenceExpression>(build(::KotlinUQualifiedReferenceExpression))
                is KtSafeQualifiedExpression -> expr<UQualifiedReferenceExpression>(build(::KotlinUSafeQualifiedExpression))
                is KtSimpleNameExpression -> expr<USimpleNameReferenceExpression>(build(::FirKotlinUSimpleReferenceExpression))
                is KtCallExpression -> expr<UCallExpression>(build(::KotlinUFunctionCallExpression))

                is KtBinaryExpression -> {
                    if (expression.operationToken == KtTokens.ELVIS) {
                        expr<UExpressionList>(build(::createElvisExpression))
                    } else {
                        expr<UBinaryExpression>(build(::FirKotlinUBinaryExpression))
                    }
                }
                is KtPrefixExpression -> expr<UPrefixExpression>(build(::KotlinUPrefixExpression))
                is KtPostfixExpression -> expr<UPostfixExpression>(build(::KotlinUPostfixExpression))

                is KtClassOrObject -> expr<UDeclarationsExpression> {
                    expression.toLightClass()?.let { lightClass ->
                        KotlinUDeclarationsExpression(givenParent).apply {
                            declarations = listOf(KotlinUClass.create(lightClass, this))
                        }
                    } ?: UastEmptyExpression(givenParent)
                }
                is KtLambdaExpression -> expr<ULambdaExpression>(build(::KotlinULambdaExpression))
                is KtFunction -> {
                    if (expression.name.isNullOrEmpty()) {
                        expr<ULambdaExpression>(build(::createLocalFunctionLambdaExpression))
                    } else {
                        expr<UDeclarationsExpression>(build(::createLocalFunctionDeclaration))
                    }
                }
                is KtAnnotatedExpression -> {
                    expression.baseExpression
                        ?.let { convertExpression(it, givenParent, requiredTypes) }
                        ?: expr<UExpression>(build(::UnknownKotlinExpression))
                }

                else -> expr<UExpression>(build(::UnknownKotlinExpression))
            }
        }
    }
}
