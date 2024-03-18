// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inline

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiReference
import org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner.CodeToInlineBuilder
import org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner.PropertyUsageReplacementStrategy
import org.jetbrains.kotlin.idea.refactoring.inline.AbstractKotlinInlinePropertyProcessor
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.CodeToInline
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.UsageReplacementStrategy
import org.jetbrains.kotlin.idea.refactoring.inline.createReplacementStrategyForProperty
import org.jetbrains.kotlin.psi.*

class KotlinInlinePropertyProcessor(
    declaration: KtProperty,
    reference: PsiReference?,
    inlineThisOnly: Boolean,
    deleteAfter: Boolean,
    isWhenSubjectVariable: Boolean,
    editor: Editor?,
    statementToDelete: KtBinaryExpression?,
    project: Project,
) : AbstractKotlinInlinePropertyProcessor(
    declaration,
    reference,
    inlineThisOnly,
    deleteAfter,
    isWhenSubjectVariable,
    editor,
    statementToDelete,
    project
) {
    override fun unwrapSpecialUsage(usage: KtReferenceExpression): KtSimpleNameExpression? {
        return null
    }

    override fun createReplacementStrategy(): UsageReplacementStrategy? {
        return createReplacementStrategyForProperty(declaration, editor, myProject, { decl, fallbackToSuperCall ->
            codeToInlineBuilder(decl, fallbackToSuperCall)
        }, { readReplacement, writeReplacement -> createStrategy(readReplacement, writeReplacement) })
    }

    override fun createStrategy(readReplacement: CodeToInline?, writeReplacement: CodeToInline?): UsageReplacementStrategy {
        return PropertyUsageReplacementStrategy(readReplacement, writeReplacement)
    }

    override fun codeToInlineBuilder(
        decl: KtDeclaration,
        fallbackToSuperCall: Boolean
    ) = CodeToInlineBuilder(
        original = decl, fallbackToSuperCall = fallbackToSuperCall
    )
}
