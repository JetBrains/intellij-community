// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.scratch

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import org.jetbrains.kotlin.idea.scratch.actions.RunScratchAction
import org.jetbrains.kotlin.idea.scratch.actions.RunScratchFromHereAction
import org.jetbrains.kotlin.idea.scratch.actions.ScratchCompilationSupport
import org.jetbrains.kotlin.idea.scratch.ui.findScratchFileEditorWithPreview
import org.jetbrains.kotlin.idea.util.application.getServiceSafe

class ScratchFileAutoRunner(private val project: Project) : DocumentListener, Disposable {
    companion object {
        fun addListener(project: Project, editor: TextEditor) {
            if (editor.getScratchFile() != null) {
                editor.editor.document.addDocumentListener(getInstance(project))
                Disposer.register(editor, Disposable {
                    editor.editor.document.removeDocumentListener(getInstance(project))
                })
            }
        }

        private fun getInstance(project: Project): ScratchFileAutoRunner = project.getServiceSafe()

        const val AUTO_RUN_DELAY_IN_SECONDS = 2
    }

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
                        RunScratchFromHereAction.doAction(scratchFile)
                    } else {
                        RunScratchAction.doAction(scratchFile, true)
                    }
                }

            }, AUTO_RUN_DELAY_IN_SECONDS * 1000, true
        )
    }

    private fun getScratchFile(file: VirtualFile, project: Project): ScratchFile? {
        val editor = FileEditorManager.getInstance(project).getSelectedEditor(file) as? TextEditor
        return editor?.findScratchFileEditorWithPreview()?.scratchFile
    }

    override fun dispose() {

    }
}