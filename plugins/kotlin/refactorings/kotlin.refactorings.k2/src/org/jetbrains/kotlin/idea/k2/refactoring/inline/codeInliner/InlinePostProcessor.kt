// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner

import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.ShortenOptions
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.codeinsight.utils.RemoveExplicitTypeArgumentsUtils
import org.jetbrains.kotlin.idea.k2.refactoring.canMoveLambdaOutsideParentheses
import org.jetbrains.kotlin.idea.k2.refactoring.inline.KotlinInlineAnonymousFunctionProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.util.areTypeArgumentsRedundant
import org.jetbrains.kotlin.idea.k2.refactoring.util.isRedundantUnit
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.AbstractInlinePostProcessor
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.USER_CODE_KEY
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression
import org.jetbrains.kotlin.resolve.ArrayFqNames.ARRAY_CALL_FQ_NAMES
import java.util.ArrayList
import kotlin.collections.asReversed
import kotlin.collections.contains
import kotlin.collections.mapNotNull
import kotlin.collections.orEmpty
import kotlin.to

object InlinePostProcessor: AbstractInlinePostProcessor() {
    override fun canMoveLambdaOutsideParentheses(expr: KtCallExpression): Boolean {
        return expr.canMoveLambdaOutsideParentheses(skipComplexCalls = false)
    }

    override fun removeRedundantUnitExpressions(pointer: SmartPsiElementPointer<KtElement>) {
        pointer.element?.forEachDescendantOfType<KtReferenceExpression> {
            if (isRedundantUnit(it)) {
                it.delete()
            }
        }
    }

    override fun removeRedundantLambdasAndAnonymousFunctions(pointer: SmartPsiElementPointer<KtElement>) {
        val element = pointer.element ?: return
        for (function in element.collectDescendantsOfType<KtFunction>().asReversed()) {
            if (function.hasBody()) {
                val call = KotlinInlineAnonymousFunctionProcessor.findCallExpression(function)
                if (call != null) {
                    KotlinInlineAnonymousFunctionProcessor.performRefactoring(call, editor = null)
                }
            }
        }
    }

    override fun shortenReferences(pointers: List<SmartPsiElementPointer<KtElement>>): List<KtElement> {
        val facility = ShortenReferencesFacility.getInstance()
        return pointers.mapNotNull { p ->
            val ktElement = p.element ?: return@mapNotNull null
            facility.shorten(ktElement, ShortenOptions.ALL_ENABLED)
            p.element
        }
    }

    override fun simplifySpreadArrayOfArguments(pointer: SmartPsiElementPointer<KtElement>) {
        val result = pointer.element ?: return
        val argumentsToExpand = ArrayList<Pair<KtValueArgument, KtCallExpression>>()

        result.forEachDescendantOfType<KtValueArgument>(canGoInside = { it.getCopyableUserData(USER_CODE_KEY) == null }) { argument ->
            if (argument.getSpreadElement() != null && !argument.isNamed()) {
                val argumentExpression = argument.getArgumentExpression() ?: return@forEachDescendantOfType
                val callExpression =
                  ((argumentExpression as? KtDotQualifiedExpression)?.selectorExpression ?: argumentExpression) as? KtCallExpression
                        ?: return@forEachDescendantOfType
                val resolved =
                  callExpression.referenceExpression()?.mainReference?.resolve() as? KtNamedDeclaration ?: return@forEachDescendantOfType
                if (ARRAY_CALL_FQ_NAMES.contains(resolved.fqName)) {
                    argumentsToExpand.add(argument to callExpression)
                }
            }
        }

        for ((argument, replacements) in argumentsToExpand) {
            argument.replaceByMultiple(replacements)
        }
    }


    private fun KtValueArgument.replaceByMultiple(expr: KtCallExpression) {
        val list = parent as KtValueArgumentList
        val arguments = expr.valueArgumentList?.arguments.orEmpty()
        if (arguments.isEmpty()) {
            list.removeArgument(this)
        } else {
            var anchor = this
            for (argument in arguments) {
                anchor = list.addArgumentAfter(argument, anchor)
            }
            list.removeArgument(this)
        }
    }

    override fun removeExplicitTypeArguments(pointer: SmartPsiElementPointer<KtElement>) {
        val result = pointer.element ?: return
        for (typeArgumentList in result.collectDescendantsOfType<KtTypeArgumentList>(canGoInside = { it.getCopyableUserData(USER_CODE_KEY) == null }).asReversed()) {
            val callExpression = typeArgumentList.parent as? KtCallExpression

            if (callExpression != null &&
                RemoveExplicitTypeArgumentsUtils.isApplicableByPsi(callExpression) &&
                analyze(typeArgumentList) { areTypeArgumentsRedundant(typeArgumentList) }) {
                typeArgumentList.delete()
            }
        }
    }

}