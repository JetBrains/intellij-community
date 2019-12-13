// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.pipenv.detectAndSetupPipEnv

/**
 * @author vlan
 */
class PythonSdkConfigurator {
  companion object {
    private fun findExistingAssociatedSdk(module: Module, existingSdks: List<Sdk>): Sdk? {
      return existingSdks
        .asSequence()
        .filter { it.sdkType is PythonSdkType && it.isAssociatedWithModule(module) }
        .sortedByDescending { it.homePath }
        .firstOrNull()
    }

    private fun findDetectedAssociatedEnvironment(module: Module, existingSdks: List<Sdk>, context: UserDataHolder): PyDetectedSdk? {
      // TODO: Move all interpreter detection away from EDT & use proper synchronization for that
      val progress = ProgressManager.getInstance()
      return progress.run(object : Task.WithResult<PyDetectedSdk?, Exception>(module.project,
                                                                              "Looking for Virtual Environments",
                                                                              false) {
        override fun compute(indicator: ProgressIndicator): PyDetectedSdk? {
          indicator.isIndeterminate = true
          detectVirtualEnvs(module, existingSdks, context).firstOrNull { it.isAssociatedWithModule(module) }?.let {
            return it
          }
          detectCondaEnvs(module, existingSdks, context).firstOrNull { it.isAssociatedWithModule(module) }?.let {
            return it
          }
          return null
        }
      })
    }

    private fun getDefaultProjectSdk(): Sdk? {
      return ProjectRootManager.getInstance(ProjectManager.getInstance().defaultProject).projectSdk?.takeIf { it.sdkType is PythonSdkType }
    }

    private fun findExistingSystemWideSdk(existingSdks: List<Sdk>) =
      existingSdks.filter { it.isSystemWide }.sortedWith(PreferredSdkComparator.INSTANCE).firstOrNull()

    private fun findDetectedSystemWideSdk(module: Module?, existingSdks: List<Sdk>, context: UserDataHolder) =
      detectSystemWideSdks(module, existingSdks, context).firstOrNull()
  }

  init {
    ApplicationManager.getApplication().messageBus.connect().subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
      override fun projectOpened(project: Project) {
        if (!project.isDefault) {
          configureSdk(project)
        }
      }
    })
  }

  private fun configureSdk(project: Project) {
    val context = UserDataHolderBase()
    if (project.pythonSdk != null) {
      return
    }
    val module = ModuleManager.getInstance(project).modules.firstOrNull() ?: return
    val existingSdks = ProjectSdksModel().apply { reset(project) }.sdks.filter { it.sdkType is PythonSdkType }

    findExistingAssociatedSdk(module, existingSdks)?.let {
      SdkConfigurationUtil.setDirectoryProjectSdk(project, it)
      module.excludeInnerVirtualEnv(it)
      return
    }

    findDetectedAssociatedEnvironment(module, existingSdks, context)?.let {
      val newSdk = it.setupAssociated(existingSdks, module.basePath) ?: return
      SdkConfigurationUtil.addSdk(newSdk)
      newSdk.associateWithModule(module, null)
      SdkConfigurationUtil.setDirectoryProjectSdk(project, newSdk)
      module.excludeInnerVirtualEnv(it)
      return
    }

    // TODO: Introduce an extension for configuring a project via a Python SDK provider
    detectAndSetupPipEnv(project, module, existingSdks)?.let {
      SdkConfigurationUtil.addSdk(it)
      SdkConfigurationUtil.setDirectoryProjectSdk(project, it)
      return
    }

    getDefaultProjectSdk()?.let {
      SdkConfigurationUtil.setDirectoryProjectSdk(project, it)
      return
    }

    findExistingSystemWideSdk(existingSdks)?.let {
      SdkConfigurationUtil.setDirectoryProjectSdk(project, it)
      return
    }

    findDetectedSystemWideSdk(module, existingSdks, context)?.let {
      SdkConfigurationUtil.createAndAddSDK(it.homePath!!, PythonSdkType.getInstance())?.apply {
        SdkConfigurationUtil.setDirectoryProjectSdk(project, this)
      }
    }
  }
}
