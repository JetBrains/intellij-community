// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inline

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.refactoring.RefactoringBundle
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.isRecursive
import org.jetbrains.kotlin.idea.refactoring.inline.AbstractKotlinInlineFunctionHandler
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.findSimpleNameReference
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction

class KotlinInlineFunctionHandler: AbstractKotlinInlineFunctionHandler<KtNamedFunction>() {
    override fun canInlineKotlinFunction(function: KtFunction): Boolean = function is KtNamedFunction && function.nameIdentifier != null

    override fun inlineKotlinFunction(
        project: Project,
        editor: Editor?,
        function: KtNamedFunction
    ) {

        val nameReference = editor?.findSimpleNameReference()

        val recursive = function.isRecursive()
        if (recursive && nameReference == null) {
            val message = RefactoringBundle.getCannotRefactorMessage(
                KotlinBundle.message("text.inline.recursive.function.is.supported.only.on.references")
            )

            return showErrorHint(project, editor, message)
        }

        val dialog = KotlinInlineNamedFunctionDialog(
            function,
            nameReference,
            allowToInlineThisOnly = recursive,
            editor = editor,
        )

        if (!isUnitTestMode()) {
            dialog.show()
        } else {
            try {
                dialog.doAction()
            } finally {
                dialog.close(DialogWrapper.OK_EXIT_CODE, true)
            }
        }
    }
}