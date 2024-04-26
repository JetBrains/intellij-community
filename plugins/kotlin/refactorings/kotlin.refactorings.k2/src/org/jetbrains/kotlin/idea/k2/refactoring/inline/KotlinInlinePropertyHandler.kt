// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inline

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiReference
import org.jetbrains.kotlin.idea.refactoring.inline.AbstractKotlinInlinePropertyHandler
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtProperty

class KotlinInlinePropertyHandler : AbstractKotlinInlinePropertyHandler(true) {

    override fun createInlinePropertyDialog(
        declaration: KtProperty,
        reference: PsiReference?,
        assignmentToDelete: KtBinaryExpression?,
        editor: Editor?
    ) = KotlinInlinePropertyDialog(
        property = declaration,
        reference = reference,
        assignmentToDelete = assignmentToDelete,
        withPreview = withPrompt,
        editor = editor
    )
}