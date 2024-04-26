// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inline

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiReference
import com.intellij.refactoring.BaseRefactoringProcessor
import org.jetbrains.kotlin.idea.refactoring.inline.AbstractKotlinInlineNamedFunctionDialog
import org.jetbrains.kotlin.psi.KtNamedFunction

class KotlinInlineNamedFunctionDialog(
    function: KtNamedFunction,
    reference: PsiReference?,
    editor: Editor?,
    allowToInlineThisOnly: Boolean,
) : AbstractKotlinInlineNamedFunctionDialog(function, reference, editor, allowToInlineThisOnly) {
    init {
        init()
    }

    override fun createProcessor(): BaseRefactoringProcessor = KotlinInlineFunctionProcessor(
        declaration = declaration,
        reference = reference,
        inlineThisOnly = isInlineThisOnly || allowToInlineThisOnly,
        deleteAfter = !isInlineThisOnly && !isKeepTheDeclaration && !allowToInlineThisOnly,
        editor = editor,
        project = project,
    )
}
