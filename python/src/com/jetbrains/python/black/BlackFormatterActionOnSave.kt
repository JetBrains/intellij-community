// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.black

import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener.ActionOnSave
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.PyBundle
import com.jetbrains.python.black.configuration.BlackFormatterConfiguration
import org.jetbrains.annotations.Nls

class BlackFormatterActionOnSave : ActionOnSave() {

  companion object {
    val LOG: Logger = thisLogger()
    const val BLACK_ACTION_ON_SAVE_ERROR_NOTIFICATION_GROUP_ID: String = "Black action on save error"
    const val BLACK_ACTION_ON_SAVE_FILE_IGNORED_NOTIFICATION_GROUP_ID: String = "Black action on save file ignored"
  }

  override fun isEnabledForProject(project: Project): Boolean = Registry.`is`("black.formatter.support.enabled")

  override fun processDocuments(project: Project, documents: Array<Document>) {
    val blackConfig = BlackFormatterConfiguration.getBlackConfiguration(project)
    if (!blackConfig.enabledOnSave) return

    val sdk = blackConfig.getSdk()
    if (sdk == null) {
      LOG.warn(PyBundle.message("black.sdk.not.configured.error", project.name))
      return
    }

    formatMultipleDocuments(project, sdk, blackConfig, documents.toList())
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
      ProgressManager.getInstance().run(
        object : Task.Backgroundable(project, PyBundle.message("black.formatting.with.black"), true) {
          override fun run(indicator: ProgressIndicator) {
            var processedFiles = 0L
            indicator.text = PyBundle.message("black.formatting.with.black")
            indicator.isIndeterminate = false

            descriptors.forEach { descriptor ->
              processedFiles++
              indicator.fraction = processedFiles / descriptors.size.toDouble()
              indicator.text = PyBundle.message("black.processing.file.name", descriptor.virtualFile.name)
              val request = BlackFormattingRequest.File(descriptor.virtualFile, descriptor.document.text)
              val response = executor.getBlackFormattingResponse(request, BlackFormatterExecutor.BLACK_DEFAULT_TIMEOUT)
              if (!indicator.isCanceled) {
                applyChanges(project, descriptor, response)
              }
            }
          }
        }
      )
    }.onFailure { exception ->
      when (exception) {
        is ProcessCanceledException -> { /* ignore */ }
        else -> {
          LOG.warn(exception)
          reportFailure(PyBundle.message("black.exception.error.message"), exception.localizedMessage, project)
        }
      }
    }
  }

  private fun applyChanges(project: Project, descriptor: Descriptor, response: BlackFormattingResponse) {
    when (response) {
      is BlackFormattingResponse.Success -> {
        if (response.formattedText != ReadAction.compute<String, Exception> { descriptor.document.text })
          WriteCommandAction.writeCommandAction(project)
            .withName(PyBundle.message("black.formatting.with.black"))
            .run<Exception> { descriptor.document.setText(response.formattedText) }
      }
      is BlackFormattingResponse.Failure -> {
        reportFailure(response.title, response.getPopupMessage(), project)
      }
      is BlackFormattingResponse.Ignored -> {
        reportIgnored(response.title, response.description, project)
      }
    }
  }

  private fun reportFailure(@Nls title: String, @Nls message: String, project: Project) {
    val notification = Notification(BLACK_ACTION_ON_SAVE_ERROR_NOTIFICATION_GROUP_ID,
                                    title,
                                    message, NotificationType.ERROR)
    Notifications.Bus.notify(notification, project)
  }

  private fun reportIgnored(@Nls title: String, @Nls message: String, project: Project) {
    val notification = Notification(BLACK_ACTION_ON_SAVE_FILE_IGNORED_NOTIFICATION_GROUP_ID,
                                    title,
                                    message, NotificationType.INFORMATION)
    notification.addAction(NotificationAction
      .createSimpleExpiring(PyBundle.message("black.advertising.service.dont.show.again.label")) {
        notification.setDoNotAskFor(project)
      })
    Notifications.Bus.notify(notification, project)
  }

  private data class Descriptor(val document: Document, val virtualFile: VirtualFile)
}