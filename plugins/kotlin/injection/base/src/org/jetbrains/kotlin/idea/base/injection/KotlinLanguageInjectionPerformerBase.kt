// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.injection

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.lang.injection.general.Injection
import com.intellij.lang.injection.general.LanguageInjectionPerformer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.util.containers.ContainerUtil
import org.intellij.plugins.intelliLang.inject.InjectedLanguage
import org.intellij.plugins.intelliLang.inject.InjectorUtils
import org.intellij.plugins.intelliLang.inject.registerSupport
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

private const val NO_VALUE_NAME = "missingValue"

private data class InjectionSplitResult(val isUnparsable: Boolean, val ranges: List<InjectorUtils.InjectionInfo>)

@ApiStatus.Internal
abstract class KotlinLanguageInjectionPerformerBase : LanguageInjectionPerformer {
    override fun isPrimary(): Boolean = true

    override fun performInjection(registrar: MultiHostRegistrar, injection: Injection, context: PsiElement): Boolean {
        if (context !is KtElement || !isSupportedElement(context)) return false

        val support = InjectorUtils.getActiveInjectionSupports()
            .firstIsInstanceOrNull<KotlinLanguageInjectionSupportBase>() ?: return false

        val language = InjectorUtils.getLanguageByString(injection.injectedLanguageId) ?: return false

        val file = context.containingKtFile
        val parts = transformToInjectionParts(injection, context) ?: return false

        if (parts.ranges.isEmpty()) return false

        InjectorUtils.registerInjection(language, file, parts.ranges, registrar) {
            it.registerSupport(support, false)
                .frankensteinInjection(parts.isUnparsable)
        }
        return true
    }

    private fun transformToInjectionParts(injection: Injection, literalOrConcatenation: KtElement): InjectionSplitResult? {
        InjectorUtils.getLanguageByString(injection.injectedLanguageId) ?: return null

        val indentHandler = literalOrConcatenation.indentHandler ?: NoIndentHandler

        fun injectionRange(literal: KtStringTemplateExpression, range: TextRange, prefix: String, suffix: String): InjectorUtils.InjectionInfo {
            TextRange.assertProperRange(range, injection)
            val injectedLanguage = InjectedLanguage.create(injection.injectedLanguageId, prefix, suffix, true)!!
            return InjectorUtils.InjectionInfo(literal, injectedLanguage, range)
        }

        tailrec fun collectInjections(
            literal: KtStringTemplateExpression?,
            children: List<PsiElement>,
            pendingPrefix: String,
            unparseable: Boolean,
            collected: MutableList<InjectorUtils.InjectionInfo>
        ): InjectionSplitResult {
            val child = children.firstOrNull() ?: return InjectionSplitResult(unparseable, collected)
            val tail = children.subList(1, children.size)
            val partOffsetInParent = child.startOffsetInParent

            when {
                child is KtBinaryExpression && child.operationToken == KtTokens.PLUS -> {
                    return collectInjections(null,
                                             ContainerUtil.concat(listOfNotNull(child.left, child.right), tail), pendingPrefix, unparseable, collected)
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
                    return collectInjections(child,
                                             ContainerUtil.concat(child.children.toList(), tail), pendingPrefix, unparseable, collected)
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

    abstract fun tryEvaluateConstant(expression: KtExpression?): String?
}