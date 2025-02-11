// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.scratch.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.fileEditor.TextEditor
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.scratch.ScratchFile
import org.jetbrains.kotlin.idea.scratch.ui.KtScratchFileEditorWithPreview
import org.jetbrains.kotlin.idea.scratch.ui.findScratchFileEditorWithPreview
import java.util.function.Supplier
import javax.swing.Icon

abstract class ScratchAction(@Nls message: Supplier<String>, icon: Icon) : AnAction(message, message, icon) {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isVisible = e.currentScratchFile != null
    }

    protected val AnActionEvent.currentScratchFile: ScratchFile?
        get() = currentScratchEditor?.scratchFile

    protected val AnActionEvent.currentScratchEditor: KtScratchFileEditorWithPreview?
        get() {
            val textEditor = getData(PlatformCoreDataKeys.FILE_EDITOR) as? TextEditor
            return textEditor?.findScratchFileEditorWithPreview()
        }
}