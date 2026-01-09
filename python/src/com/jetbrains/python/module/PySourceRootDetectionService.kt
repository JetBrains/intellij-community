// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.module

import com.intellij.ide.projectView.actions.MarkRootsManager
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.storage.EntityChange
import com.jetbrains.python.PyBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.model.java.JavaSourceRootType
import java.io.File

@ApiStatus.Internal
@Service( Service.Level.PROJECT)
@State(
  name = "PySourceRootDetectionService",
  storages = [Storage(value = "pySourceRootDetection.xml", roamingType = RoamingType.DISABLED)]
)
class PySourceRootDetectionService(
  private val project: Project,
  private val cs: CoroutineScope
) : ModuleRootListener, SerializablePersistentStateComponent<PySourceRootDetectionService.HiddenSourcesState>(HiddenSourcesState()) {
  init {
    cs.launch {
     collectWorkspaceModelChanges()
    }
  }

  private suspend fun collectWorkspaceModelChanges() {
    WorkspaceModel.getInstance(project).eventLog.collect { event ->
      if (!Registry.`is`("python.source.root.suggest.quickfix.auto.apply")) {
        return@collect
      }
      event.getChanges(SourceRootEntity::class.java).forEach { change ->
        val sourceRootToHide: SourceRootEntity = when (change) {
          is EntityChange.Added<SourceRootEntity> -> null
          is EntityChange.Replaced<SourceRootEntity> -> change.oldEntity
          is EntityChange.Removed<SourceRootEntity> -> change.oldEntity
        } ?: return@forEach

        val sourceRootToHideVirtualFile = sourceRootToHide.url.virtualFile ?: return@forEach
        hideSourceRoot(sourceRootToHideVirtualFile)
      }
    }
  }

  private fun isSourceRootHidden(sourceRoot: VirtualFile): Boolean {
    return sourceRoot.path in state.sourcePathsSet
  }

  private fun hideSourceRoot(sourceRoot: VirtualFile) {
    updateState {
      state.copy(
        sourcePathsSet = state.sourcePathsSet + sourceRoot.path
      )
    }
  }

  fun markAsSourceRoot(sourceRoot: VirtualFile, showNotification: Boolean = true) {
    cs.launch {
      writeAction {
        val module = ModuleUtil.findModuleForFile(sourceRoot, project) ?: return@writeAction
        val model = ModuleRootManager.getInstance(module).modifiableModel
        val entry = MarkRootsManager.findContentEntry(model, sourceRoot) ?: return@writeAction
        // In general, `markAsSourceRoot` is called only for folders that are not source roots yet.
        // Double-check in case this method was called twice before the folder was marked as a source root.
        val isAlreadyMarkedAsSourceRoot = entry.getSourceFolders().any { it.file == sourceRoot }
        if (isAlreadyMarkedAsSourceRoot) {
          return@writeAction
        }
        entry.addSourceFolder(sourceRoot, JavaSourceRootType.SOURCE)
        model.commit()

        if (showNotification) {
          showNotification(sourceRoot)
        }
      }
    }
  }

  private fun unmarkAsSourceRoot(sourceRoot: VirtualFile, doNotSuggestSourceRootAgain: Boolean = true) {
    if (doNotSuggestSourceRootAgain) {
      hideSourceRoot(sourceRoot)
    }
    cs.launch {
      writeAction {
        val module = ModuleUtil.findModuleForFile(sourceRoot, project) ?: return@writeAction
        val model = ModuleRootManager.getInstance(module).modifiableModel
        val entry = MarkRootsManager.findContentEntry(model, sourceRoot) ?: return@writeAction
        val toRemove = entry.sourceFolders.firstOrNull { it.file == this || it.url == sourceRoot.url }
        if (toRemove != null) {
          entry.removeSourceFolder(toRemove)
        }
        model.commit()
      }
    }
  }

  fun onSourceRootDetected(sourceRootPath: VirtualFile) {
    if (isSourceRootHidden(sourceRootPath)) {
      return
    }
    markAsSourceRoot(sourceRootPath, showNotification = true)
  }

  private fun getSourceRootVisibleName(sourceRoot: VirtualFile): @NlsSafe String {
    val projectDir = project.guessProjectDir() ?: return sourceRoot.name
    val relativePath = VfsUtilCore.getRelativePath(sourceRoot, projectDir, File.separatorChar)
    return relativePath?.replace("\\", "/") ?: sourceRoot.name
  }

  private fun showNotification(sourceRoot: VirtualFile) {
    val message = PyBundle.message("python.source.root.detection.confirm.notification.title", getSourceRootVisibleName(sourceRoot))
    val group = NotificationGroupManager.getInstance().getNotificationGroup("Python source root detection")
    val notification = group.createNotification(message, NotificationType.INFORMATION)

    notification.addAction(OkAction)
    notification.addAction(RevertAction(sourceRoot))
    notification.addAction(MuteAction)

    notification.notify(project)
  }

  @TestOnly
  fun resetState() {
    updateState { HiddenSourcesState() }
  }

  @ApiStatus.Internal
  data class HiddenSourcesState(
    @JvmField
    val sourcePathsSet: Set<String> = emptySet(),
  )

  private object OkAction : NotificationAction(
    PyBundle.message("python.source.root.detection.confirm.notification.action.ok")
  ) {
    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
      notification.expire()
    }
  }

  private inner class RevertAction(private val sourceRoot: VirtualFile) : NotificationAction(
    PyBundle.message("python.source.root.detection.confirm.notification.action.revert")
  ) {
    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
      unmarkAsSourceRoot(sourceRoot)
      notification.expire()
    }
  }

  private object MuteAction : NotificationAction(
    PyBundle.message("python.source.root.detection.confirm.notification.action.mute")
  ) {
    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
      Registry.get("python.source.root.suggest.quickfix.auto.apply").setValue(false)
      notification.expire()
    }
  }
}
