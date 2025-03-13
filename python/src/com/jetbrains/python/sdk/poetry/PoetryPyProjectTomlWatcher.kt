// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts.NotificationContent
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.PythonSdkCoroutineService
import com.jetbrains.python.sdk.findAmongRoots
import com.jetbrains.python.sdk.pythonSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls

private class PoetryProjectTomlListener(val module: Module) : DocumentListener {
  private val LOCK_NOTIFICATION_GROUP by lazy { NotificationGroupManager.getInstance().getNotificationGroup("pyproject.toml Watcher") }
  private val notificationActive = Key.create<Boolean>("PyProjectToml.notification.active")
  private val documentChangedMutex = Mutex()

  override fun documentChanged(event: DocumentEvent) {
    service<PythonSdkCoroutineService>().cs.launch {
      if (!FileDocumentManager.getInstance().isDocumentUnsaved(event.document)) return@launch

      documentChangedMutex.withLock(module) {
        if (isNotificationActive()) return@launch
        setNotificationActive(true)
      }

      notifyPyProjectTomlChanged(module)
    }
  }

  @NotificationContent
  private suspend fun content(): @Nls String = if (getPoetryVersion()?.let { it < "1.1.1" } == true) {
    PyBundle.message("python.sdk.poetry.pip.file.notification.content")
  }
  else {
    PyBundle.message("python.sdk.poetry.pip.file.notification.content.without.updating")
  }

  private suspend fun poetryLock(module: Module) = withContext(Dispatchers.IO) { findAmongRoots(module, POETRY_LOCK) }

  fun isNotificationActive(): Boolean = module.getUserData(notificationActive) == true

  fun setNotificationActive(isActive: Boolean): Unit = module.putUserData(notificationActive, isActive.takeIf { it })

  private suspend fun notifyPyProjectTomlChanged(module: Module) {
    @Suppress("DialogTitleCapitalization") val title = when (poetryLock(module)) {
      null -> PyBundle.message("python.sdk.poetry.pip.file.lock.not.found")
      else -> PyBundle.message("python.sdk.poetry.pip.file.lock.out.of.date")
    }

    val notification = LOCK_NOTIFICATION_GROUP.createNotification(title, content(), NotificationType.INFORMATION).setListener(
      NotificationListener { notification, event ->
        FileDocumentManager.getInstance().saveAllDocuments()
        when (event.description) {
          "#lock" ->
            runPoetryInBackground(module, listOf("lock"), PyBundle.message("python.sdk.poetry.pip.file.notification.locking"))
          "#noupdate" ->
            runPoetryInBackground(module, listOf("lock", "--no-update"),
                                  PyBundle.message("python.sdk.poetry.pip.file.notification.locking.without.updating"))
          "#update" ->
            runPoetryInBackground(module, listOf("update"), PyBundle.message("python.sdk.poetry.pip.file.notification.updating"))
        }
        notification.expire()
      })

    notification.whenExpired {
      service<PythonSdkCoroutineService>().cs.launch {
        setNotificationActive(false)
      }
    }

    notification.notify(module.project)
  }
}

/**
 * Watches for edits in PyProjectToml inside modules with a poetry SDK set.
 */
internal class PoetryPyProjectTomlWatcher : EditorFactoryListener {
  private val changeListenerKey = Key.create<PoetryProjectTomlListener>("Poetry.PyProjectToml.change.listener")

  @OptIn(ExperimentalCoroutinesApi::class)
  private val queueDispatcher = Dispatchers.Default.limitedParallelism(1, "PoetryPyProjectTomlWatcher Queue Dispatcher")


  private fun Document.addPoetryListener(module: Module) = synchronized(changeListenerKey) {
    getUserData(changeListenerKey)?.let { return@addPoetryListener }

    PoetryProjectTomlListener(module).let {
      addDocumentListener(it)
      putUserData(changeListenerKey, it)
    }
  }

  private fun Document.removePoetryListenerIfExists() = synchronized(changeListenerKey) {
    getUserData(changeListenerKey)?.let { listener ->
      removeDocumentListener(listener)
      putUserData(changeListenerKey, null)
    }
  }

  private fun queuedLaunch(block: suspend () -> Unit) {
    service<PythonSdkCoroutineService>().cs.launch {
      withContext(queueDispatcher) {
        block()
      }
    }
  }

  override fun editorCreated(event: EditorFactoryEvent) = queuedLaunch {
    val project = event.editor.project ?: return@queuedLaunch

    val editablePyProjectTomlFile = event.editor.document.virtualFile?.takeIf { it.name == PY_PROJECT_TOML } ?: return@queuedLaunch
    val module = getModule(editablePyProjectTomlFile, project) ?: return@queuedLaunch
    val poetryManagedTomlFile = module.takeIf { it.pythonSdk?.isPoetry == true }?.let { pyProjectToml(it) }

    if (editablePyProjectTomlFile == poetryManagedTomlFile) {
      event.editor.document.addPoetryListener(module)
    }
  }

  override fun editorReleased(event: EditorFactoryEvent) = queuedLaunch {
    event.editor.document.removePoetryListenerIfExists()
  }

  private val Document.virtualFile: VirtualFile?
    get() = FileDocumentManager.getInstance().getFile(this)

  private suspend fun getModule(file: VirtualFile, project: Project): Module? = withContext(Dispatchers.IO) {
    ModuleUtil.findModuleForFile(file, project)
  }
}