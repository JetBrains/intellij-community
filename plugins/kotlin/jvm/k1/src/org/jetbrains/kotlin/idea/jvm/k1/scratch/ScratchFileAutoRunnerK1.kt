// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.k1.scratch

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import org.jetbrains.kotlin.idea.jvm.k1.scratch.actions.RunScratchAction
import org.jetbrains.kotlin.idea.jvm.k1.scratch.actions.RunScratchFromHereAction
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFile
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFileAutoRunner
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFileAutoRunner.Companion.AUTO_RUN_DELAY_MS
import org.jetbrains.kotlin.idea.jvm.shared.scratch.actions.ScratchCompilationSupport
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ui.findScratchFileEditorWithPreview

class ScratchFileAutoRunnerK1(private val project: Project) : ScratchFileAutoRunner, Disposable {
    private val myAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

    override fun documentChanged(event: DocumentEvent) {
        val file = FileDocumentManager.getInstance().getFile(event.document) ?: return

        if (project.isDisposed) return
        val scratchFile = getScratchFile(file, project) as? K1KotlinScratchFile ?: return
        if (!scratchFile.options.isInteractiveMode) return

        if (event.newFragment.isNotBlank()) {
            runScratch(scratchFile)
        }
    }

    private fun runScratch(scratchFile: K1KotlinScratchFile) {
        myAlarm.cancelAllRequests()

        if (ScratchCompilationSupport.isInProgress(scratchFile) && !scratchFile.options.isRepl) {
            ScratchCompilationSupport.forceStop()
        }

        myAlarm.addRequest(
            {
                scratchFile.ktFile?.takeIf { it.isValid && !scratchFile.hasErrors() }?.let {
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

fun getScratchFile(file: VirtualFile, project: Project): ScratchFile? {
    val editor = FileEditorManager.getInstance(project).getSelectedEditor(file) as? TextEditor
    return editor?.findScratchFileEditorWithPreview()?.scratchFile
}