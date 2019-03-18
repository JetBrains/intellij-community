// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.PackagesToolWindowTab
import com.intellij.openapi.ui.PackagesToolWindowTabFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.webcore.packaging.PackagesNotificationPanel
import com.jetbrains.python.packaging.PyPackage
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.packaging.PyPackageManagers
import com.jetbrains.python.sdk.PythonSdkType

class PyPackagesToolWindowTabFactory : PackagesToolWindowTabFactory {

  /**
   * Allows to reduce updates if shown sdk has not been changed.
   */
  private var currentSdk: Sdk? = null

  /**
   * Allows to overcome infinite self-refreshing on packages-refreshed event
   * because [PyInstalledPackagesPanel] also generates this event.
   */
  private var currentPackages: List<PyPackage>? = null

  override fun createContent(project: Project, parentDisposable: Disposable): PackagesToolWindowTab {
    val panel = PyInstalledPackagesPanel(project, PackagesNotificationPanel())
    updateForCurrentFile(project, panel)

    val connection = project.messageBus.connect(parentDisposable)

    // another file has been opened
    connection.subscribe(
      FileEditorManagerListener.FILE_EDITOR_MANAGER,
      object : FileEditorManagerListener {
        override fun selectionChanged(event: FileEditorManagerEvent) = updateForFile(project, panel, event.newFile)
      }
    )

    // installed packages have been possibly changed
    connection.subscribe(
      PyPackageManager.PACKAGE_MANAGER_TOPIC,
      PyPackageManager.Listener {
        updateForCurrentFile(project, panel)
      }
    )

    return PackagesToolWindowTab("Python", panel)
  }

  private fun updateForCurrentFile(project: Project, panel: PyInstalledPackagesPanel) {
    updateForFile(project, panel, FileEditorManager.getInstance(project).selectedFiles.firstOrNull())
  }

  private fun updateForFile(project: Project, panel: PyInstalledPackagesPanel, file: VirtualFile?) {
    updateForSdk(project, panel, file?.let { PythonSdkType.findPythonSdk(ModuleUtilCore.findModuleForFile(it, project)) })
  }

  private fun updateForSdk(project: Project, panel: PyInstalledPackagesPanel, sdk: Sdk?) {
    val packageManagers = PyPackageManagers.getInstance()

    val latestPackages = sdk?.let { packageManagers.forSdk(it).packages }
    if (currentSdk != sdk || currentPackages != latestPackages) {
      currentSdk = sdk
      currentPackages = latestPackages

      panel.updatePackages(sdk?.let { packageManagers.getManagementService(project, it) })
      panel.updateNotifications(sdk)
    }
  }
}