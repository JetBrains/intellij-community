// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.refactoring.inline

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.help.HelpManager
import com.intellij.psi.PsiReference
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.HelpID
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.idea.refactoring.inline.AbstractKotlinInlineDialog
import org.jetbrains.kotlin.psi.KtTypeAlias
import kotlin.reflect.KMutableProperty1

class KotlinInlineTypeAliasDialog(
    typeAlias: KtTypeAlias,
    reference: PsiReference?,
    editor: Editor?,
) : AbstractKotlinInlineDialog<KtTypeAlias>(typeAlias, reference, editor) {
    init {
        init()
    }

    override fun doHelpAction() = HelpManager.getInstance().invokeHelp(HelpID.INLINE_VARIABLE)
    override val inlineThisOption: KMutableProperty1<KotlinCommonRefactoringSettings, Boolean> get() = KotlinCommonRefactoringSettings::INLINE_TYPE_ALIAS_THIS
    override val inlineKeepOption: KMutableProperty1<KotlinCommonRefactoringSettings, Boolean> get() = KotlinCommonRefactoringSettings::INLINE_TYPE_ALIAS_KEEP
    override fun createProcessor(): BaseRefactoringProcessor = KotlinInlineTypeAliasProcessor(
        declaration = declaration,
        reference = reference,
        inlineThisOnly = isInlineThisOnly,
        deleteAfter = !isInlineThisOnly && !isKeepTheDeclaration,
        editor = editor,
        project = project,
    )
}
