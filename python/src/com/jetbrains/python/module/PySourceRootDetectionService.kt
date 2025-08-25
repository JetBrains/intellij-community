// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.module

import com.intellij.ide.projectView.actions.MarkRootsManager
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.util.xmlb.annotations.Transient
import com.jetbrains.python.PyBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.jps.model.java.JavaSourceRootType
import java.io.File

@Service( Service.Level.PROJECT)
@State(
  name = "PySourceRootDetectionService",
  storages = [Storage(value = "pySourceRootDetection.xml", roamingType = RoamingType.DISABLED)]
)
internal class PySourceRootDetectionService(
  private val project: Project,
  private val cs: CoroutineScope
) : ModuleRootListener, PersistentStateComponent<PySourceRootDetectionService.HiddenSourcesState> {
  private val mutableState = MutableStateFlow(State())

  init {
    cs.launch {
     collectWorkspaceModelChanges()
    }
  }

  private suspend fun collectWorkspaceModelChanges() {
    WorkspaceModel.getInstance(project).eventLog.collect { event ->
      event.getChanges(SourceRootEntity::class.java).forEach { change ->
        val modifiedSourceRootEntities: List<SourceRootEntity> = when (change) {
          is EntityChange.Added<SourceRootEntity> -> listOf(change.newEntity)
          is EntityChange.Replaced<SourceRootEntity> -> listOf(change.newEntity, change.oldEntity)
          is EntityChange.Removed<SourceRootEntity> -> listOf(change.oldEntity)
        } ?: return@forEach

        val sourceRootVirtualFiles = modifiedSourceRootEntities.mapNotNull { it.url.virtualFile }

        mutableState.update {
          val detectedSourceRootsWithoutChanged = it.detectedSourceRoots.filterNot {
            sourceRootVirtualFiles.contains(it)
          }
          it.copy(detectedSourceRoots = detectedSourceRootsWithoutChanged.toSet())
        }
      }
    }
  }

  fun isSourceRootHidden(sourceRoot: VirtualFile): Boolean {
    return sourceRoot.path in mutableState.value.hiddenSourceRoots.sourcePaths
  }

  fun hideSourceRoot(sourceRoot: VirtualFile) {
    mutableState.update {
      it.copy(
        hiddenSourceRoots = it.hiddenSourceRoots.copy(
          sourcePaths = it.hiddenSourceRoots.sourcePaths + sourceRoot.path
        )
      )
    }
  }

  fun markAsSourceRoot(sourceRoot: VirtualFile, showNotification: Boolean = true) = cs.launch {
    writeAction {
      val module = ModuleUtil.findModuleForFile(sourceRoot, project) ?: return@writeAction
      val model = ModuleRootManager.getInstance(module).modifiableModel
      val entry = MarkRootsManager.findContentEntry(model, sourceRoot) ?: return@writeAction
      entry.addSourceFolder(sourceRoot, JavaSourceRootType.SOURCE)
      model.commit()

      if (showNotification) {
        showNotification(sourceRoot)
      }
    }
  }

  fun unmarkAsSourceRoot(sourceRoot: VirtualFile) {
    val module = ModuleUtil.findModuleForFile(sourceRoot, project) ?: return
    val model = ModuleRootManager.getInstance(module).modifiableModel
    val entry = MarkRootsManager.findContentEntry(model, sourceRoot) ?: return
    val toRemove = entry.sourceFolders.firstOrNull { it.file == this || it.url == sourceRoot.url }
    if (toRemove != null) {
      entry.removeSourceFolder(toRemove)
    }
    model.commit()
  }

  fun onSourceRootDetected(sourceRootPath: VirtualFile) {
    if (isSourceRootHidden(sourceRootPath) || sourceRootPath in mutableState.value.detectedSourceRoots) {
      return
    }
    mutableState.update {
      it.copy(detectedSourceRoots = it.detectedSourceRoots + sourceRootPath)
    }
  }

  fun getSourceRootsToReport(): List<VirtualFile> = mutableState.value.sourceRootsToReport

  fun getSourceRootVisibleName(sourceRoot: VirtualFile): @NlsSafe String {
    val projectDir = project.guessProjectDir() ?: return sourceRoot.name
    val relativePath = VfsUtilCore.getRelativePath(sourceRoot, projectDir, File.separatorChar)
    return relativePath?.replace("\\", "/") ?: sourceRoot.name
  }

  private fun showNotification(sourceRoot: VirtualFile) {
    val message = PyBundle.message("python.source.root.detection.confirm.notification.title", getSourceRootVisibleName(sourceRoot))
    val group = NotificationGroupManager.getInstance().getNotificationGroup("Python source root detection")
    val notification = group.createNotification(message, NotificationType.INFORMATION)

    // Ok action just closes the notification
    notification.addAction(NotificationAction.createSimpleExpiring(
      PyBundle.message("python.source.root.detection.confirm.notification.action.ok")
    ) { /* no-op, expiring */ })

    // Revert action removes the just added source root
    notification.addAction(object : NotificationAction(PyBundle.message("python.source.root.detection.confirm.notification.action.revert")) {
      override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        ApplicationManager.getApplication().runWriteAction {
          unmarkAsSourceRoot(sourceRoot)
        }
        notification.expire()
      }
    })

    notification.notify(project)
  }

  override fun getState(): HiddenSourcesState {
    return mutableState.value.hiddenSourceRoots
  }

  override fun loadState(state: HiddenSourcesState) {
    mutableState.update {
      it.copy(hiddenSourceRoots = state)
    }
  }

  internal data class HiddenSourcesState(
    val sourcePaths: List<String> = emptyList(),
  ) {
    @Transient
    val sourcePathsSet: Set<String> = sourcePaths.toSet()
  }

  internal data class State(
    val detectedSourceRoots: Set<VirtualFile> = emptySet(),
    val hiddenSourceRoots: HiddenSourcesState = HiddenSourcesState(),
  ) {
    val sourceRootsToReport: List<VirtualFile> = detectedSourceRoots.filterNot { it.path in hiddenSourceRoots.sourcePathsSet }
  }
}
