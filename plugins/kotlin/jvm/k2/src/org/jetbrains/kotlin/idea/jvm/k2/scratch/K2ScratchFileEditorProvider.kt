// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.k2.scratch

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.jvm.shared.scratch.isKotlinScratch
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ui.KtScratchFileEditorProvider
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ui.KtScratchFileEditorWithPreview

internal class K2ScratchFileEditorProvider : KtScratchFileEditorProvider() {
    override fun accept(project: Project, file: VirtualFile): Boolean = file.isValid && file.isKotlinScratch

    override suspend fun createFileEditor(
        project: Project, file: VirtualFile, document: Document?, editorCoroutineScope: CoroutineScope
    ): FileEditor {
        val textEditorProvider = TextEditorProvider.getInstance()

        val scratchFile = K2KotlinScratchFile(project, file, editorCoroutineScope.childScope(K2KotlinScratchFile::class.java.simpleName))

        val mainEditor = textEditorProvider.createFileEditor(
            project = project,
            file = scratchFile.file,
            document = readAction { FileDocumentManager.getInstance().getDocument(scratchFile.file) },
            editorCoroutineScope = editorCoroutineScope,
        )

        val editorFactory = serviceAsync<EditorFactory>()

        return withContext(Dispatchers.EDT) {
            val viewer = editorFactory.createViewer(editorFactory.createDocument(""), scratchFile.project, EditorKind.PREVIEW)
            Disposer.register(mainEditor, Disposable { editorFactory.releaseEditor(viewer) })
            val previewEditor = textEditorProvider.getTextEditor(viewer)
            K2ScratchFileEditorWithPreview(scratchFile, mainEditor, previewEditor)
        }
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val scratchFile = runBlockingCancellable {
            K2KotlinScratchFile(project, file, this)
        }
        return K2ScratchFileEditorWithPreview.create(scratchFile)
    }
}

private class K2ScratchFileEditorWithPreview(
    val kotlinScratchFile: K2KotlinScratchFile, sourceTextEditor: TextEditor, previewTextEditor: TextEditor
) : KtScratchFileEditorWithPreview(kotlinScratchFile, sourceTextEditor, previewTextEditor) {

    init {
        kotlinScratchFile.executor.addOutputHandler(commonPreviewOutputHandler)
    }

    override fun dispose() {
        kotlinScratchFile.executor.stop()
        super.dispose()
    }

    override fun createToolbar(): ActionToolbar = ScratchTopPanelK2(kotlinScratchFile).actionsToolbar

    companion object {
        fun create(scratchFile: K2KotlinScratchFile): KtScratchFileEditorWithPreview {
            val textEditorProvider = TextEditorProvider.getInstance()

            val mainEditor = textEditorProvider.createEditor(scratchFile.project, scratchFile.file) as TextEditor
            val editorFactory = EditorFactory.getInstance()

            val viewer = editorFactory.createViewer(editorFactory.createDocument(""), scratchFile.project, EditorKind.PREVIEW)
            Disposer.register(mainEditor, Disposable { editorFactory.releaseEditor(viewer) })

            val previewEditor = textEditorProvider.getTextEditor(viewer)

            return K2ScratchFileEditorWithPreview(scratchFile, mainEditor, previewEditor)
        }
    }
}
