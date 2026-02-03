// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inline

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiReference
import org.jetbrains.kotlin.idea.refactoring.inline.AbstractKotlinInlinePropertyHandler
import org.jetbrains.kotlin.idea.refactoring.inline.AbstractKotlinInlinePropertyProcessor
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtProperty

class KotlinInlinePropertyHandler : AbstractKotlinInlinePropertyHandler(true) {
    override fun createProcessor(
        declaration: KtProperty,
        reference: PsiReference?,
        inlineThisOnly: Boolean,
        deleteAfter: Boolean,
        isWhenSubjectVariable: Boolean,
        editor: Editor?,
        statementToDelete: KtBinaryExpression?,
        project: Project
    ): AbstractKotlinInlinePropertyProcessor = KotlinInlinePropertyProcessor(
        declaration, reference, inlineThisOnly, deleteAfter, isWhenSubjectVariable, editor, statementToDelete, project
    )
}