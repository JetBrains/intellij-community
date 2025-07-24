// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.pipenv

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.jetbrains.python.PyBundle
import com.jetbrains.python.onFailure
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.*
import com.jetbrains.python.util.ShowingMessageErrorSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * Watches for edits in Pipfiles inside modules with a pipenv SDK set.
 */
internal class PipEnvPipFileWatcher : EditorFactoryListener {
  private val changeListenerKey = Key.create<DocumentListener>("Pipfile.change.listener")
  private val notificationActive = Key.create<Boolean>("Pipfile.notification.active")

  override fun editorCreated(event: EditorFactoryEvent) {
    PyPackageCoroutine.launch(event.editor.project) {
      val project = event.editor.project
      if (project == null || !isPipFileEditor(event.editor)) return@launch
      val listener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          val document = event.document
          val module = document.virtualFile?.getModule(project) ?: return
          if (FileDocumentManager.getInstance().isDocumentUnsaved(document)) {
            PyPackageCoroutine.launch(project) {
              notifyPipFileChanged(module)
            }
          }
        }
      }
      with(event.editor.document) {
        addDocumentListener(listener)
        putUserData(changeListenerKey, listener)
      }
    }
  }

  override fun editorReleased(event: EditorFactoryEvent) {
    val listener = event.editor.getUserData(changeListenerKey) ?: return
    event.editor.document.removeDocumentListener(listener)
  }

  private enum class PipEnvEvent(val description: String) {
    LOCK("#lock"),
    UPDATE("#update")
  }

  private suspend fun notifyPipFileChanged(module: Module) {
    if (module.getUserData(notificationActive) == true) return
    val title = when {
      getPipFileLock(module) == null -> PyBundle.message("python.sdk.pipenv.pip.file.lock.not.found")
      else -> PyBundle.message("python.sdk.pipenv.pip.file.lock.out.of.date")
    }
    val content = PyBundle.message("python.sdk.pipenv.pip.file.notification.content")
    val notification = withContext(Dispatchers.EDT) {
      LOCK_NOTIFICATION_GROUP.createNotification(title, content, NotificationType.INFORMATION)
    }
      .setListener(NotificationListener { notification, event ->
        notification.expire()
        module.putUserData(notificationActive, null)
        runInEdt { FileDocumentManager.getInstance().saveAllDocuments() }
        when (event.description) {
          PipEnvEvent.LOCK.description -> runPipEnvInBackground(module, listOf("lock"),
                                                                PyBundle.message("python.sdk.pipenv.pip.file.notification.locking"))
          PipEnvEvent.UPDATE.description -> runPipEnvInBackground(module, listOf("update", "--dev"),
                                                                  PyBundle.message("python.sdk.pipenv.pip.file.notification.updating"))
        }
      })
    module.putUserData(notificationActive, true)
    notification.whenExpired {
      module.putUserData(notificationActive, null)
    }
    notification.notify(module.project)
  }

  private fun runPipEnvInBackground(module: Module, args: List<String>, @NlsContexts.ProgressTitle description: String) {
    PyPackageCoroutine.launch(module.project) {
      withBackgroundProgress(module.project, description) {
        val sdk = module.pythonSdk ?: return@withBackgroundProgress
        runPipEnv(sdk.associatedModulePath?.let { Path.of(it) }, *args.toTypedArray()).onFailure {
          ShowingMessageErrorSync.emit(it)
        }

        withContext(Dispatchers.Default) {
          PythonSdkUtil.getSitePackagesDirectory(sdk)?.refresh(true, true)
          sdk.associatedModuleDir?.refresh(true, false)
        }
      }
    }
  }

  private suspend fun isPipFileEditor(editor: Editor): Boolean {
    val file = editor.document.virtualFile ?: return false
    if (file.name != PipEnvFileHelper.PIP_FILE) return false
    val project = editor.project ?: return false
    val module = file.getModule(project) ?: return false
    if (PipEnvFileHelper.pipFile(module) != file) return false
    return module.pythonSdk?.isPipEnv == true
  }

  private val Document.virtualFile: VirtualFile?
    get() = FileDocumentManager.getInstance().getFile(this)

  private fun VirtualFile.getModule(project: Project): Module? =
    ModuleUtil.findModuleForFile(this, project)

  private val LOCK_NOTIFICATION_GROUP = Cancellation.forceNonCancellableSectionInClassInitializer {
    NotificationGroupManager.getInstance().getNotificationGroup("Pipfile Watcher")
  }

  private suspend fun getPipFileLock(module: Module): VirtualFile? = withContext(Dispatchers.IO) { findAmongRoots(module, PipEnvFileHelper.PIP_FILE_LOCK) }
}