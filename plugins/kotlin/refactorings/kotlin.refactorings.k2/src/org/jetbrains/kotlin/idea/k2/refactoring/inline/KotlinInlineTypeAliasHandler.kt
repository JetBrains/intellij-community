// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.refactoring.inline

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.refactoring.HelpID
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.inline.KotlinInlineActionHandler
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.findSimpleNameReference
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtTypeAlias

class KotlinInlineTypeAliasHandler : KotlinInlineActionHandler() {
    override val helpId: String get() = HelpID.INLINE_VARIABLE

    override val refactoringName: String get() = KotlinBundle.message("title.inline.type.alias")

    override fun canInlineKotlinElement(element: KtElement): Boolean = element is KtTypeAlias

    override fun inlineKotlinElement(project: Project, editor: Editor?, element: KtElement) {
        val typeAlias = element as? KtTypeAlias ?: return
        if (!checkSources(project, editor, element)) return

        typeAlias.name ?: return
        typeAlias.getTypeReference() ?: return

        val dialog = KotlinInlineTypeAliasDialog(
            element,
            editor?.findSimpleNameReference(),
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
