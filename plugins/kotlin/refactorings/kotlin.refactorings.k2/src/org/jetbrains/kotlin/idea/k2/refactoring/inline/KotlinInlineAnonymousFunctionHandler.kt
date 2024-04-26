// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.refactoring.inline

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.inline.AbstractKotlinInlineFunctionHandler
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtNamedFunction

class KotlinInlineAnonymousFunctionHandler : AbstractKotlinInlineFunctionHandler<KtFunction>() {
    override fun canInlineKotlinFunction(function: KtFunction): Boolean =
        function is KtNamedFunction && function.nameIdentifier == null || function is KtFunctionLiteral

    override fun inlineKotlinFunction(project: Project, editor: Editor?, function: KtFunction) {
        val call = KotlinInlineAnonymousFunctionProcessor.findCallExpression(function)
        if (call == null) {
            val key = if (function is KtFunctionLiteral) {
                "refactoring.cannot.be.applied.to.lambda.expression.without.invocation"
            } else {
                "refactoring.cannot.be.applied.to.anonymous.function.without.invocation"
            }

            return showErrorHint(project, editor, KotlinBundle.message(key, refactoringName))
        }

        KotlinInlineAnonymousFunctionProcessor(function, call, editor, project).run()
    }
}
