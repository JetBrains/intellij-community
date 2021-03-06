package com.jetbrains.packagesearch.intellij.plugin.api

import com.intellij.history.LocalHistory
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy.REQUEST_CONFIRMATION
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.patchers.FileAccess

class PackageSearchVirtualFileAccess(
    private val project: Project,
    private val file: VirtualFile
) : FileAccess {

    private fun findDocument(): Document {
        val document = FileDocumentManager.getInstance().getDocument(file)

        if (document == null) {
            val message = PackageSearchBundle.message("packagesearch.add.dependency.error.could.not.find.document.for.file", file)
            throw IllegalArgumentException(message)
        }

        return document
    }

    override fun loadText(): String = ReadAction.compute<String, RuntimeException> {
        findDocument().text
    }

    override fun saveText(newText: String) = WriteAction.runAndWait<RuntimeException> {
        val doc = findDocument()

        val normalizedText = newText.replace(System.lineSeparator(), "\n")

        LocalHistory.getInstance().putUserLabel(project, PackageSearchBundle.message("packagesearch.actions.history.label"))

        CommandProcessor.getInstance().executeCommand(project, {
            doc.setText(normalizedText)
        }, PackageSearchBundle.message("packagesearch.actions.history.label"), "packagesearch", REQUEST_CONFIRMATION, doc)
    }
}
