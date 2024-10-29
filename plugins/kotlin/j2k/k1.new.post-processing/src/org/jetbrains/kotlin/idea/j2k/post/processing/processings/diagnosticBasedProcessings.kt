// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing.processings

import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.diagnostics.DiagnosticWithParameters2
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.psi.isNullExpression
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.utils.removeUnnecessaryParentheses
import org.jetbrains.kotlin.idea.j2k.post.processing.diagnosticBasedProcessing
import org.jetbrains.kotlin.idea.quickfix.NumberConversionFix
import org.jetbrains.kotlin.idea.quickfix.RemoveUselessCastFix
import org.jetbrains.kotlin.idea.quickfix.isNumberConversionAvailable
import org.jetbrains.kotlin.idea.quickfix.prepareNumberConversionElementContext
import org.jetbrains.kotlin.j2k.unpackedReferenceToProperty
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isNullable
import org.jetbrains.kotlin.types.typeUtil.isNothing
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf
import org.jetbrains.kotlin.types.typeUtil.makeNotNullable

internal val fixValToVarDiagnosticBasedProcessing =
    diagnosticBasedProcessing(
        Errors.VAL_REASSIGNMENT, Errors.CAPTURED_VAL_INITIALIZATION, Errors.CAPTURED_MEMBER_VAL_INITIALIZATION
    ) { element: KtExpression, _ ->
        val property = element.unpackedReferenceToProperty() ?: return@diagnosticBasedProcessing
        if (!property.isVar) {
            property.valOrVarKeyword.replace(KtPsiFactory(element.project).createVarKeyword())
        }
    }

internal val fixTypeMismatchDiagnosticBasedProcessing =
    diagnosticBasedProcessing(Errors.TYPE_MISMATCH) { element: PsiElement, diagnostic ->
        @Suppress("UNCHECKED_CAST")
        val diagnosticWithParameters =
            diagnostic as? DiagnosticWithParameters2<KtExpression, KotlinType, KotlinType>
                ?: return@diagnosticBasedProcessing
        val expectedType = diagnosticWithParameters.a
        val realType = diagnosticWithParameters.b
        when {
            realType.makeNotNullable().isSubtypeOf(expectedType.makeNotNullable())
                    && realType.isNullable()
                    && !expectedType.isNullable()
            -> {
                val psiFactory = KtPsiFactory(element.project)
                val replaced = element.replace(psiFactory.createExpressionByPattern("($0)!!", element.text)) as KtPostfixExpression
                val parenthesizedExpression = replaced.baseExpression as KtParenthesizedExpression
                if (KtPsiUtil.areParenthesesUseless(parenthesizedExpression)) {
                    parenthesizedExpression.removeUnnecessaryParentheses()
                }
            }

            element is KtExpression && isNumberConversionAvailable(realType, expectedType) -> {
                val elementContext = prepareNumberConversionElementContext(realType, expectedType)
                val fix = NumberConversionFix(element, elementContext)
                fix.asIntention().invoke(element.project, null, element.containingFile)
            }

            element is KtLambdaExpression
                    && expectedType.isNothing() -> {
                for (valueParameter in element.valueParameters) {
                    valueParameter.typeReference?.delete()
                    valueParameter.colon?.delete()
                }
            }
        }
    }

internal val removeUselessCastDiagnosticBasedProcessing =
    diagnosticBasedProcessing<KtBinaryExpressionWithTypeRHS>(Errors.USELESS_CAST) { element, _ ->
        if (element.left.isNullExpression()) return@diagnosticBasedProcessing
        val expression = RemoveUselessCastFix.invoke(element)

        val variable = expression.parent as? KtProperty
        if (variable != null && expression == variable.initializer && variable.isLocal) {
            val ref = ReferencesSearch.search(variable, LocalSearchScope(variable.containingFile)).findAll().singleOrNull()
            if (ref != null && ref.element is KtSimpleNameExpression) {
                ref.element.replace(expression)
                variable.delete()
            }
        }
    }

internal val removeUnnecessaryNotNullAssertionDiagnosticBasedProcessing =
    diagnosticBasedProcessing<KtSimpleNameExpression>(Errors.UNNECESSARY_NOT_NULL_ASSERTION) { element, _ ->
        val exclExclExpr = element.parent as KtUnaryExpression
        val baseExpression = exclExclExpr.baseExpression ?: return@diagnosticBasedProcessing
        val context = baseExpression.analyze(BodyResolveMode.PARTIAL_WITH_DIAGNOSTICS)
        if (context.diagnostics.forElement(element).any { it.factory == Errors.UNNECESSARY_NOT_NULL_ASSERTION }) {
            exclExclExpr.replace(baseExpression)
        }
    }