// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.scratch.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import org.jetbrains.kotlin.idea.KotlinJvmBundle
import org.jetbrains.kotlin.idea.scratch.ui.findScratchFileEditorWithPreview

class ClearScratchAction : ScratchAction(
    KotlinJvmBundle.message("scratch.clear.button"),
    AllIcons.Actions.GC
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val selectedEditor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        val textEditor = TextEditorProvider.getInstance().getTextEditor(selectedEditor)

        textEditor.findScratchFileEditorWithPreview()?.clearOutputHandlers()
    }
}