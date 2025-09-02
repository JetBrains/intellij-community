// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jvm.k2.scratch

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFile
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFileAutoRunner
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ui.findScratchFileEditorWithPreview

@OptIn(FlowPreview::class)
class ScratchFileAutoRunnerK2(private val project: Project, private val scope: CoroutineScope) : ScratchFileAutoRunner {
    private val flow = MutableSharedFlow<K2KotlinScratchFile>()

    init {
        scope.launch {
            flow.debounce(ScratchFileAutoRunner.AUTO_RUN_DELAY_MS).collect {
                it.executor.execute()
            }
        }
    }

    fun submitRun(file: K2KotlinScratchFile) {
        scope.launch {
            flow.emit(file)
        }
    }

    override fun documentChanged(event: DocumentEvent) {
        val file = FileDocumentManager.getInstance().getFile(event.document) ?: return

        if (project.isDisposed) return
        val scratchFile = getScratchFile(file, project) as? K2KotlinScratchFile ?: return
        if (!scratchFile.options.isInteractiveMode) return

        scope.launch {
            flow.emit(scratchFile)
        }
    }

    private fun getScratchFile(file: VirtualFile, project: Project): ScratchFile? {
        val editor = FileEditorManager.getInstance(project).getSelectedEditor(file) as? TextEditor
        return editor?.findScratchFileEditorWithPreview()?.scratchFile
    }
}