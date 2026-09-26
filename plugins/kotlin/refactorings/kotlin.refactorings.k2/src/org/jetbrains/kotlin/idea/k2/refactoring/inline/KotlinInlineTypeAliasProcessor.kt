// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.refactoring.inline

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiReference
import org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner.TypeAliasUsageReplacementStrategy
import org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner.fullyExpandCall
import org.jetbrains.kotlin.idea.refactoring.inline.AbstractKotlinInlineNamedDeclarationProcessor
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.UsageReplacementStrategy
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTypeAlias

class KotlinInlineTypeAliasProcessor(
    declaration: KtTypeAlias,
    reference: PsiReference?,
    inlineThisOnly: Boolean,
    deleteAfter: Boolean,
    editor: Editor?,
    project: Project,
) : AbstractKotlinInlineNamedDeclarationProcessor<KtTypeAlias>(
    declaration = declaration,
    reference = reference,
    inlineThisOnly = inlineThisOnly,
    deleteAfter = deleteAfter,
    editor = editor,
    project = project,
) {

    override fun createReplacementStrategy(): UsageReplacementStrategy = TypeAliasUsageReplacementStrategy(declaration)
    override fun unwrapSpecialUsage(usage: KtReferenceExpression): KtSimpleNameExpression? {
        return fullyExpandCall(usage)
    }
}
