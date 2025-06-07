// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.inline

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.help.HelpManager
import com.intellij.psi.PsiReference
import com.intellij.refactoring.HelpID
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtNamedFunction
import kotlin.reflect.KMutableProperty1

abstract class AbstractKotlinInlineNamedFunctionDialog(
    function: KtNamedFunction,
    reference: PsiReference?,
    editor: Editor?,
    protected val allowToInlineThisOnly: Boolean,
) : AbstractKotlinInlineDialog<KtNamedFunction>(function, reference, editor) {
    init {
        init()
    }

    override fun canInlineThisOnly() = allowToInlineThisOnly
    override fun doHelpAction() = HelpManager.getInstance().invokeHelp(
        if (declaration is KtConstructor<*>) HelpID.INLINE_CONSTRUCTOR else HelpID.INLINE_METHOD
    )

    override val inlineThisOption: KMutableProperty1<KotlinCommonRefactoringSettings, Boolean> get() = KotlinCommonRefactoringSettings::INLINE_METHOD_THIS
    override val inlineKeepOption: KMutableProperty1<KotlinCommonRefactoringSettings, Boolean> get() = KotlinCommonRefactoringSettings::INLINE_METHOD_KEEP
}