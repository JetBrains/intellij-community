// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inline

import org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner.CallableUsageReplacementStrategy
import org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner.CodeToInlineBuilder
import org.jetbrains.kotlin.idea.refactoring.inline.AbstractKotlinInlineNamedDeclarationProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiReference
import org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner.fullyExpandCall
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.CodeToInline
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.UsageReplacementStrategy
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.buildCodeToInline
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

class KotlinInlineFunctionProcessor(
    declaration: KtNamedFunction,
    reference: PsiReference?,
    inlineThisOnly: Boolean,
    deleteAfter: Boolean,
    editor: Editor?,
    project: Project,
) : AbstractKotlinInlineNamedDeclarationProcessor<KtNamedFunction>(
    declaration = declaration,
    reference = reference,
    inlineThisOnly = inlineThisOnly,
    deleteAfter = deleteAfter,
    editor = editor,
    project = project,
) {
    override fun createReplacementStrategy(): UsageReplacementStrategy? {
        return createUsageReplacementStrategyForFunction(declaration, editor, fallbackToSuperCall = false)
    }
    override fun unwrapSpecialUsage(usage: KtReferenceExpression): KtSimpleNameExpression? {
        return fullyExpandCall(usage)
    }
}

fun createUsageReplacementStrategyForFunction(
    function: KtNamedFunction,
    editor: Editor?,
    fallbackToSuperCall: Boolean = false,
): UsageReplacementStrategy? {
    val codeToInline = createCodeToInlineForFunction(function, editor, fallbackToSuperCall = fallbackToSuperCall) ?: return null
    return CallableUsageReplacementStrategy(codeToInline, inlineSetter = false)
}

fun createCodeToInlineForFunction(
    function: KtNamedFunction,
    editor: Editor?,
    fallbackToSuperCall: Boolean = false,
): CodeToInline? = buildCodeToInline(
    function,
    function.bodyExpression!!,
    function.hasBlockBody(),
    editor,
    CodeToInlineBuilder(
        original = function,
        fallbackToSuperCall = fallbackToSuperCall,
    ),
)
