// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.injection

import com.intellij.injected.editor.InjectionMeta
import com.intellij.lang.injection.general.Injection
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.util.containers.ContainerUtil.concat
import org.intellij.plugins.intelliLang.inject.InjectedLanguage
import org.intellij.plugins.intelliLang.inject.InjectorUtils
import org.intellij.plugins.intelliLang.inject.InjectorUtils.InjectionInfo
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

data class InjectionSplitResult(val isUnparsable: Boolean, val ranges: List<InjectionInfo>)

internal var PsiElement.trimIndent: String? by UserDataProperty(InjectionMeta.INJECTION_INDENT)

fun transformToInjectionParts(injection: Injection, literalOrConcatenation: KtElement): InjectionSplitResult? {
    InjectorUtils.getLanguageByString(injection.injectedLanguageId) ?: return null

    val indentHandler = literalOrConcatenation.indentHandler ?: NoIndentHandler

    fun injectionRange(literal: KtStringTemplateExpression, range: TextRange, prefix: String, suffix: String): InjectionInfo {
        TextRange.assertProperRange(range, injection)
        val injectedLanguage = InjectedLanguage.create(injection.injectedLanguageId, prefix, suffix, true)!!
        return InjectionInfo(literal, injectedLanguage, range)
    }

    tailrec fun collectInjections(
        literal: KtStringTemplateExpression?,
        children: List<PsiElement>,
        pendingPrefix: String,
        unparseable: Boolean,
        collected: MutableList<InjectionInfo>
    ): InjectionSplitResult {
        val child = children.firstOrNull() ?: return InjectionSplitResult(unparseable, collected)
        val tail = children.subList(1, children.size)
        val partOffsetInParent = child.startOffsetInParent

        when {
            child is KtBinaryExpression && child.operationToken == KtTokens.PLUS -> {
                return collectInjections(null, concat(listOfNotNull(child.left, child.right), tail), pendingPrefix, unparseable, collected)
            }
            child is KtStringTemplateExpression -> {
                if (child.children.isEmpty()) {
                    // empty range to save injection in the empty string
                    collected += injectionRange(
                        child,
                        ElementManipulators.getValueTextRange(child),
                        pendingPrefix,
                        if (tail.isEmpty()) injection.suffix else ""
                    )
                }
                return collectInjections(child, concat(child.children.toList(), tail), pendingPrefix, unparseable, collected)
            }
            literal == null -> {
                val (prefix, myUnparseable) = makePlaceholder(child)
                return collectInjections(literal = null, tail, pendingPrefix + prefix, unparseable || myUnparseable, collected)
            }
            child is KtLiteralStringTemplateEntry || child is KtEscapeStringTemplateEntry -> {
                val consequentStringsCount = tail.asSequence()
                    .takeWhile { it is KtLiteralStringTemplateEntry || it is KtEscapeStringTemplateEntry }
                    .count()

                val lastChild = children[consequentStringsCount]
                val remaining = tail.subList(consequentStringsCount, tail.size)

                val rangesIterator = indentHandler.getUntrimmedRanges(
                    literal,
                    TextRange.create(
                        partOffsetInParent,
                        lastChild.startOffsetInParent + lastChild.textLength
                    )
                ).iterator()

                var pendingPrefixForTrimmed = pendingPrefix
                while (rangesIterator.hasNext()) {
                    val trimAwareRange = rangesIterator.next()
                    collected += injectionRange(
                        literal, trimAwareRange, pendingPrefixForTrimmed,
                        if (!rangesIterator.hasNext() && remaining.isEmpty()) injection.suffix else ""
                    )
                    pendingPrefixForTrimmed = ""
                }

                return collectInjections(literal, remaining, "", unparseable, collected)
            }
            else -> {
                if (pendingPrefix.isNotEmpty() || collected.isEmpty()) {
                    // Store pending prefix before creating a new one,
                    // or if it is a first part then create a dummy injection to distinguish "inner" prefixes
                    collected += injectionRange(literal, getRangeForPlaceholder(literal, child) { startOffset }, pendingPrefix, "")
                }

                val (prefix, myUnparseable) = makePlaceholder(child)

                if (tail.isEmpty()) {
                    // There won't be more elements, so create part with prefix right away
                    val rangeForPlaceHolder = getRangeForPlaceholder(literal, child) { endOffset }
                    collected +=
                        if (literal.textRange.contains(child.textRange))
                            injectionRange(literal, rangeForPlaceHolder, prefix, injection.suffix)
                        else
                        // when the child is not a string we use the end of the current literal anyway (the concantenation case)
                            injectionRange(literal, rangeForPlaceHolder, "", prefix + injection.suffix)
                }
                return collectInjections(literal, tail, prefix, unparseable || myUnparseable, collected)
            }
        }
    }

    return collectInjections(null, listOf(literalOrConcatenation), injection.prefix, false, ArrayList())
}

private inline fun getRangeForPlaceholder(
    literal: KtStringTemplateExpression,
    child: PsiElement,
    selector: TextRange.() -> Int
): TextRange {
    val literalRange = literal.textRange
    val childRange = child.textRange
    if (literalRange.contains(childRange))
        return TextRange.from(selector(childRange) - literalRange.startOffset, 0)
    else
        return ElementManipulators.getValueTextRange(literal).let { TextRange.from(selector(it), 0) }
}

private fun makePlaceholder(child: PsiElement): Pair<String, Boolean> = when (child) {
    is KtSimpleNameStringTemplateEntry ->
        tryEvaluateConstant(child.expression)?.let { it to false } ?: ((child.expression?.text ?: NO_VALUE_NAME) to true)
    is KtBlockStringTemplateEntry ->
        tryEvaluateConstant(child.expression)?.let { it to false } ?: (NO_VALUE_NAME to true)
    else ->
        ((child.text ?: NO_VALUE_NAME) to true)
}

private fun tryEvaluateConstant(ktExpression: KtExpression?) =
    ktExpression?.let { expression ->
        ConstantExpressionEvaluator.getConstant(expression, expression.analyze())
            ?.takeUnless { it.isError }
            ?.getValue(TypeUtils.NO_EXPECTED_TYPE)
            ?.safeAs<String>()
    }

private const val NO_VALUE_NAME = "missingValue"

internal fun flattenBinaryExpression(root: KtBinaryExpression): Sequence<KtExpression> = sequence {
    root.left?.let { lOperand ->
        if (lOperand.isConcatenationExpression())
            yieldAll(flattenBinaryExpression(lOperand as KtBinaryExpression))
        else
            yield(lOperand)
    }
    root.right?.let { yield(it) }
}

fun PsiElement.isConcatenationExpression(): Boolean = this is KtBinaryExpression && this.operationToken == KtTokens.PLUS