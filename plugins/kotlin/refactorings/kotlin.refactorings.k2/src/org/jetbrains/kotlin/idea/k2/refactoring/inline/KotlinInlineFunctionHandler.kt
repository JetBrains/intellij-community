// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inline

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.refactoring.RefactoringBundle
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.inline.AbstractKotlinInlineFunctionHandler
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction

class KotlinInlineFunctionHandler: AbstractKotlinInlineFunctionHandler<KtNamedFunction>() {
    override fun canInlineKotlinFunction(function: KtFunction): Boolean = true

    override fun inlineKotlinFunction(
        project: Project,
        editor: Editor?,
        function: KtNamedFunction
    ) {
        val message = RefactoringBundle.getCannotRefactorMessage(
            KotlinBundle.message("text.inline.function.not.supported")
        )

        return showErrorHint(project, editor, message)
    }
}