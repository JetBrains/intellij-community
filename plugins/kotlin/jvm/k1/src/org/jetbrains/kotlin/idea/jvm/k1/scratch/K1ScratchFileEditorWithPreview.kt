// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jvm.k1.scratch

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ui.KtScratchFileEditorWithPreview

class K1ScratchFileEditorWithPreview(
  val kotlinScratchFile: K1KotlinScratchFile, sourceTextEditor: TextEditor, previewTextEditor: TextEditor
) : KtScratchFileEditorWithPreview(kotlinScratchFile, sourceTextEditor, previewTextEditor) {
    init {
        kotlinScratchFile.compilingScratchExecutor?.addOutputHandler(commonPreviewOutputHandler)
        kotlinScratchFile.replScratchExecutor?.addOutputHandler(commonPreviewOutputHandler)
    }

    override fun dispose() {
        kotlinScratchFile.replScratchExecutor?.stop()
        kotlinScratchFile.compilingScratchExecutor?.stop()
        super.dispose()
    }

    override fun createToolbar(): ActionToolbar = ScratchTopPanel(kotlinScratchFile).actionsToolbar

    companion object {
        fun create(scratchFile: K1KotlinScratchFile): KtScratchFileEditorWithPreview {
            val textEditorProvider = TextEditorProvider.Companion.getInstance()

            val mainEditor = textEditorProvider.createEditor(scratchFile.project, scratchFile.file) as TextEditor
            val editorFactory = EditorFactory.getInstance()

            val viewer = editorFactory.createViewer(editorFactory.createDocument(""), scratchFile.project, EditorKind.PREVIEW)
            Disposer.register(mainEditor, Disposable { editorFactory.releaseEditor(viewer) })

            val previewEditor = textEditorProvider.getTextEditor(viewer)

            return K1ScratchFileEditorWithPreview(scratchFile, mainEditor, previewEditor)
        }
    }
}