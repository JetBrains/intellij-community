// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.scratch

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.scratch.ScratchFileAutoRunner.Companion.AUTO_RUN_DELAY_MS
import org.jetbrains.kotlin.idea.scratch.actions.RunScratchAction
import org.jetbrains.kotlin.idea.scratch.actions.RunScratchFromHereAction
import org.jetbrains.kotlin.idea.scratch.actions.ScratchCompilationSupport
import org.jetbrains.kotlin.idea.scratch.ui.findScratchFileEditorWithPreview

interface ScratchFileAutoRunner : DocumentListener {
    companion object {

        fun addListener(project: Project, editor: TextEditor) {
            if (editor.getScratchFile() != null) {
                editor.editor
                    .document
                    .addDocumentListener(
                        getInstance(project),
                        editor,
                    )
            }
        }

        const val AUTO_RUN_DELAY_MS: Long = 2_000

        @JvmStatic
        fun getInstance(project: Project): ScratchFileAutoRunner = project.service<ScratchFileAutoRunner>()
    }
}

@OptIn(FlowPreview::class)
class ScratchFileAutoRunnerK2(private val project: Project, private val scope: CoroutineScope) : ScratchFileAutoRunner {
    private val flow = MutableSharedFlow<ScratchFile>()

    init {
        scope.launch {
            flow.debounce(AUTO_RUN_DELAY_MS).collect {
                it.k2ScratchExecutor?.execute()
            }
        }
    }

    suspend fun submitRun(file: ScratchFile) {
        flow.emit(file)
    }

    override fun documentChanged(event: DocumentEvent) {
        val file = FileDocumentManager.getInstance().getFile(event.document) ?: return

        if (project.isDisposed) return
        val scratchFile = getScratchFile(file, project) ?: return
        if (!scratchFile.options.isInteractiveMode) return

        scope.launch {
            flow.emit(scratchFile)
        }
    }

}

class ScratchFileAutoRunnerK1(private val project: Project) : ScratchFileAutoRunner, Disposable {
    private val myAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

    override fun documentChanged(event: DocumentEvent) {
        val file = FileDocumentManager.getInstance().getFile(event.document) ?: return

        if (project.isDisposed) return
        val scratchFile = getScratchFile(file, project) ?: return
        if (!scratchFile.options.isInteractiveMode) return

        if (event.newFragment.isNotBlank()) {
            runScratch(scratchFile)
        }
    }

    private fun runScratch(scratchFile: ScratchFile) {
        myAlarm.cancelAllRequests()

        if (ScratchCompilationSupport.isInProgress(scratchFile) && !scratchFile.options.isRepl) {
            ScratchCompilationSupport.forceStop()
        }

        myAlarm.addRequest(
            {
                scratchFile.ktScratchFile?.takeIf { it.isValid && !scratchFile.hasErrors() }?.let {
                    if (scratchFile.options.isRepl) {
                        RunScratchFromHereAction.Handler.doAction(scratchFile)
                    } else {
                        RunScratchAction.Handler.doAction(scratchFile, true)
                    }
                }

            }, AUTO_RUN_DELAY_MS.toInt(), true
        )
    }

    override fun dispose() {

    }
}

private fun getScratchFile(file: VirtualFile, project: Project): ScratchFile? {
    val editor = FileEditorManager.getInstance(project).getSelectedEditor(file) as? TextEditor
    return editor?.findScratchFileEditorWithPreview()?.scratchFile
}