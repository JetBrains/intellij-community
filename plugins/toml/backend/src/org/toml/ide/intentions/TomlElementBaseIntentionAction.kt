/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.intentions

import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

/**
 * A base class for implementing intentions: actions available via "light bulb" / `Alt+Enter`.
 *
 * The cool thing about intentions is their UX: there is a huge number of intentions,
 * and they all can be invoked with a single `Alt + Enter` shortcut. This is possible
 * because at the position of the cursor only small number of intentions is applicable.
 *
 * So, intentions consists of two functions: [findApplicableContext] functions determines
 * if the intention can be applied at the given position, it is used to populate "light bulb" list.
 * [invoke] is called when the user selects the intention from the list and must apply the changes.
 *
 * The context collected by [findApplicableContext] is gathered into [Ctx] object and is passed to
 * [invoke]. In general, [invoke] should be infallible: if you need to check if some element is not
 * null, do this in [findApplicableContext] and pass the element via the context.
 *
 * [findApplicableContext] is executed under a read action, and [invoke] under a write action.
 */
abstract class TomlElementBaseIntentionAction<Ctx> : BaseElementAtCaretIntentionAction() {

    /**
     * Return `null` if the intention is not applicable, otherwise collect and return
     * all the necessary info to actually apply the intention.
     */
    abstract fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Ctx?

    abstract fun invoke(project: Project, editor: Editor, ctx: Ctx)

    final override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        val ctx = findApplicableContext(project, editor, element) ?: return

        if (startInWriteAction() && !IntentionPreviewUtils.isPreviewElement(element)) {
            checkWriteAccessAllowed()
        }

        invoke(project, editor, ctx)
    }

    final override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        checkReadAccessAllowed()

        return findApplicableContext(project, editor, element) != null
    }
}

private fun checkWriteAccessAllowed() {
    check(ApplicationManager.getApplication().isWriteAccessAllowed) {
        "Needs write action"
    }
}

private fun checkReadAccessAllowed() {
    check(ApplicationManager.getApplication().isReadAccessAllowed) {
        "Needs read action"
    }
}
