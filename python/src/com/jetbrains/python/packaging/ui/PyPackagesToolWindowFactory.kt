// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging.ui

import com.intellij.ProjectTopics
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.webcore.packaging.PackagesNotificationPanel
import com.jetbrains.python.packaging.PyPackage
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.packaging.PyPackageManagers
import com.jetbrains.python.sdk.PythonSdkType

class PyPackagesToolWindowFactory : ToolWindowFactory, DumbAware {

  /**
   * Allows to reduce updates if shown sdk has not been changed.
   */
  private var currentSdk: Sdk? = null

  /**
   * Allows to overcome infinite self-refreshing on packages-refreshed event
   * because [PyInstalledPackagesPanel] also generates this event.
   */
  private var currentPackages: List<PyPackage>? = null

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val panel = PyInstalledPackagesPanel(project, PackagesNotificationPanel())
    updateForCurrentFile(project, panel, true)

    val content = ContentFactory.SERVICE.getInstance().createContent(panel, null, false)
    val connection = project.messageBus.connect(content)

    // another file has been opened
    connection.subscribe(
      FileEditorManagerListener.FILE_EDITOR_MANAGER,
      object : FileEditorManagerListener {
        override fun selectionChanged(event: FileEditorManagerEvent) = updateForFile(project, panel, event.newFile, false)
      }
    )

    // sdk has been possibly changed
    connection.subscribe(
      ProjectTopics.PROJECT_ROOTS,
      object : ModuleRootListener {
        override fun rootsChanged(event: ModuleRootEvent) = updateForCurrentFile(project, panel, true)
      }
    )

    // installed packages have been possibly changed
    connection.subscribe(
      PyPackageManager.PACKAGE_MANAGER_TOPIC,
      PyPackageManager.Listener {
        // skip packages that have been refreshed for another sdk
        if (currentSdk == it) updateForSdk(project, panel, it, false)
      }
    )

    toolWindow.contentManager.addContent(content)
  }

  private fun updateForCurrentFile(project: Project, panel: PyInstalledPackagesPanel, force: Boolean) {
    updateForFile(project, panel, FileEditorManager.getInstance(project).selectedFiles.firstOrNull(), force)
  }

  private fun updateForFile(project: Project, panel: PyInstalledPackagesPanel, file: VirtualFile?, force: Boolean) {
    updateForSdk(project, panel, file?.let { PythonSdkType.findPythonSdk(ModuleUtilCore.findModuleForFile(it, project)) }, force)
  }

  private fun updateForSdk(project: Project, panel: PyInstalledPackagesPanel, sdk: Sdk?, force: Boolean) {
    val latestPackages = sdk?.let { PyPackageManagers.getInstance().forSdk(it).packages }

    if (force || currentSdk != sdk || currentPackages != latestPackages) {
      currentSdk = sdk
      currentPackages = latestPackages

      panel.updatePackages(sdk?.let { PyPackageManagers.getInstance().getManagementService(project, it) })
      panel.updateNotifications(sdk)
    }
  }
}