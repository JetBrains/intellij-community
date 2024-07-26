// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.serviceContainer.AlreadyDisposedException
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.baseDir
import com.jetbrains.python.sdk.pythonSdk
import org.apache.tuweni.toml.Toml
import org.jetbrains.annotations.Nls
import com.intellij.notification.NotificationListener

/**
 *  This source code is edited by @koxudaxi Koudai Aono <koxudaxi@gmail.com>
 */

const val PY_PROJECT_TOML: String = "pyproject.toml"

fun getPyProjectTomlForPoetry(virtualFile: VirtualFile): Pair<Long, VirtualFile?> {
  return Pair(virtualFile.modificationStamp, try {
    ReadAction.compute<VirtualFile, Throwable> {
      Toml.parse(virtualFile.inputStream).getTable("tool.poetry")?.let { virtualFile }
    }
  }
  catch (e: Throwable) {
    null
  })
}

/**
 * The PyProject.toml found in the main content root of the module.
 */
val pyProjectTomlCache = mutableMapOf<String, Pair<Long, VirtualFile?>>()
val Module.pyProjectToml: VirtualFile?
  get() =
    baseDir?.findChild(PY_PROJECT_TOML)?.let { virtualFile ->
      (this.name + virtualFile.path).let { key ->
        pyProjectTomlCache.getOrPut(key) { getPyProjectTomlForPoetry(virtualFile) }.let { pair ->
          when (virtualFile.modificationStamp) {
            pair.first -> pair.second
            else -> pyProjectTomlCache.put(key, getPyProjectTomlForPoetry(virtualFile))?.second
          }
        }
      }
    }

/**
 * Watches for edits in PyProjectToml inside modules with a poetry SDK set.
 */
class PyProjectTomlWatcher : EditorFactoryListener {
  private val changeListenerKey = Key.create<DocumentListener>("PyProjectToml.change.listener")
  private val notificationActive = Key.create<Boolean>("PyProjectToml.notification.active")
  private val content: @Nls String = if (poetryVersion?.let { it < "1.1.1" } == true) {
    PyBundle.message("python.sdk.poetry.pip.file.notification.content")
  }
  else {
    PyBundle.message("python.sdk.poetry.pip.file.notification.content.without.updating")
  }

  override fun editorCreated(event: EditorFactoryEvent) {
    val project = event.editor.project
    if (project == null || !isPyProjectTomlEditor(event.editor)) return
    val listener = object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        try {
          val document = event.document
          val module = document.virtualFile?.getModule(project) ?: return
          // TODO: Should we remove listener when a sdk is changed to non-poetry sdk?
          //                    if (!isPoetry(module.project)) {
          //                        with(document) {
          //                            putUserData(notificationActive, null)
          //                            val listener = getUserData(changeListenerKey) ?: return
          //                            removeDocumentListener(listener)
          //                            putUserData(changeListenerKey, null)
          //                            return
          //                        }
          //                    }
          if (FileDocumentManager.getInstance().isDocumentUnsaved(document)) {
            notifyPyProjectTomlChanged(module)
          }
        }
        catch (_: AlreadyDisposedException) {
        }
      }
    }
    with(event.editor.document) {
      addDocumentListener(listener)
      putUserData(changeListenerKey, listener)
    }
  }

  override fun editorReleased(event: EditorFactoryEvent) {
    val listener = event.editor.getUserData(changeListenerKey) ?: return
    event.editor.document.removeDocumentListener(listener)
  }

  private fun notifyPyProjectTomlChanged(module: Module) {
    if (module.getUserData(notificationActive) == true) return
    @Suppress("DialogTitleCapitalization") val title = when (module.poetryLock) {
      null -> PyBundle.message("python.sdk.poetry.pip.file.lock.not.found")
      else -> PyBundle.message("python.sdk.poetry.pip.file.lock.out.of.date")
    }
    val notification = LOCK_NOTIFICATION_GROUP.createNotification(title, content, NotificationType.INFORMATION).setListener(
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
        module.putUserData(notificationActive, null)
      })
    module.putUserData(notificationActive, true)
    notification.whenExpired {
      module.putUserData(notificationActive, null)
    }
    notification.notify(module.project)
  }

  private fun isPyProjectTomlEditor(editor: Editor): Boolean {
    val file = editor.document.virtualFile ?: return false
    if (file.name != PY_PROJECT_TOML) return false
    val project = editor.project ?: return false
    val module = file.getModule(project) ?: return false
    val sdk = module.pythonSdk ?: return false
    if (!sdk.isPoetry) return false
    return module.pyProjectToml == file
  }

  private val Document.virtualFile: VirtualFile?
    get() = FileDocumentManager.getInstance().getFile(this)

  private fun VirtualFile.getModule(project: Project): Module? =
    ModuleUtil.findModuleForFile(this, project)

  private val LOCK_NOTIFICATION_GROUP by lazy { NotificationGroupManager.getInstance().getNotificationGroup("pyproject.toml Watcher") }
}
