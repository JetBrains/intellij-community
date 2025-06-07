package org.jetbrains.kotlin.idea.jvm.shared.scratch

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project

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