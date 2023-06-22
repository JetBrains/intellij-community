// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.black

import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener.ActionOnSave
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.progressStep
import com.intellij.openapi.progress.runBlockingModal
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.PyBundle
import com.jetbrains.python.black.configuration.BlackFormatterConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import kotlin.coroutines.cancellation.CancellationException

class BlackFormatterActionOnSave : ActionOnSave() {

  companion object {
    val LOG = thisLogger()
  }

  override fun isEnabledForProject(project: Project): Boolean = Registry.`is`("black.formatter.support.enabled")

  override fun processDocuments(project: Project, documents: Array<Document?>) {
    val blackConfig = BlackFormatterConfiguration.getBlackConfiguration(project)
    if (!blackConfig.enabledOnSave) return

    val sdk = blackConfig.getSdk(project)
    if (sdk == null) {
      LOG.warn(PyBundle.message("black.sdk.not.configured.error", project.name))
      return
    }

    formatMultipleDocuments(project, sdk, blackConfig, documents.filterNotNull().toList())
  }

  private fun formatMultipleDocuments(project: Project,
                                      sdk: Sdk,
                                      blackConfig: BlackFormatterConfiguration,
                                      documents: List<Document>) {
    val manager = FileDocumentManager.getInstance()

    val executor = try {
      BlackFormatterExecutor(project, sdk, blackConfig)
    }
    catch (e: Exception) {
      reportFailure(PyBundle.message("black.exception.error.message"), e.localizedMessage, project)
      return
    }

    val descriptors = documents
      .mapNotNull { document -> manager.getFile(document)?.let { document to it } }
      .filter { BlackFormatterUtil.isFileApplicable(it.second) }
      .map { Descriptor(it.first, it.second) }

    runCatching {
      runBlockingModal(project, PyBundle.message("black.formatting.with.black")) {
        var processedFiles = 0L

        descriptors.forEach { descriptor ->
          processedFiles++
          progressStep(processedFiles / descriptors.size.toDouble(),
                       PyBundle.message("black.processing.file.name", descriptor.virtualFile.name)) {
            val request = BlackFormattingRequest.File(descriptor.document.text, descriptor.virtualFile)
            val response = executor.getBlackFormattingResponse(request, BlackFormatterExecutor.BLACK_DEFAULT_TIMEOUT)
            applyChanges(project, descriptor, response)
          }
        }
      }
    }.onFailure { exception ->
      when (exception) {
        is CancellationException -> { /* ignore */ }
        else -> {
          LOG.warn(exception)
          reportFailure(PyBundle.message("black.exception.error.message"), exception.localizedMessage, project)
        }
      }
    }
  }

  private suspend fun applyChanges(project: Project, descriptor: Descriptor, response: BlackFormattingResponse) {
    when (response) {
      is BlackFormattingResponse.Success -> {
        withContext(Dispatchers.EDT) {
          WriteCommandAction
            .runWriteCommandAction(project,
                                   null,
                                   null,
                                   { descriptor.document.setText(response.formattedText) })
        }
      }
      is BlackFormattingResponse.Failure -> {
        reportFailure(response.title, response.description, project)
      }
      is BlackFormattingResponse.Ignored -> {
        reportIgnored(response.title, response.description, project)
      }
    }
  }

  private fun reportFailure(@Nls title: String, @Nls message: String, project: Project) {
    Notifications.Bus.notify(
      Notification(BlackFormattingService.NOTIFICATION_GROUP_ID,
                   title,
                   message, NotificationType.ERROR), project)
  }

  // [TODO] add `do not show again` option
  private fun reportIgnored(@Nls title: String, @Nls message: String, project: Project) {
    Notifications.Bus.notify(
      Notification(BlackFormattingService.NOTIFICATION_GROUP_ID,
                   title,
                   message, NotificationType.INFORMATION), project)
  }

  private data class Descriptor(val document: Document, val virtualFile: VirtualFile)
}