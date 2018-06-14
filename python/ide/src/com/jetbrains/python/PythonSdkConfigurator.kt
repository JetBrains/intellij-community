// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.DirectoryProjectConfigurator
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.pipenv.detectAndSetupPipEnv

/**
 * @author vlan
 */
class PythonSdkConfigurator : DirectoryProjectConfigurator {
  companion object {
    fun findExistingAssociatedSdk(module: Module, existingSdks: List<Sdk>): Sdk? {
      return existingSdks
        .asSequence()
        .filter { it.sdkType is PythonSdkType && it.isAssociatedWithModule(module) }
        .sortedByDescending { it.homePath }
        .firstOrNull()
    }

    fun findDetectedAssociatedEnvironment(module: Module, existingSdks: List<Sdk>): PyDetectedSdk? {
      detectVirtualEnvs(module, existingSdks).firstOrNull { it.isAssociatedWithModule(module) }?.let {
        return it
      }
      detectCondaEnvs(module, existingSdks).firstOrNull { it.isAssociatedWithModule(module) }?.let {
        return it
      }
      return null
    }

    private fun findExistingSystemWideSdk(existingSdks: List<Sdk>) =
      existingSdks.filter { it.isSystemWide }.sortedWith(PreferredSdkComparator.INSTANCE).firstOrNull()

    private fun findDetectedSystemWideSdk(existingSdks: List<Sdk>) =
      detectSystemWideSdks(existingSdks).firstOrNull()
  }

  override fun configureProject(project: Project?, baseDir: VirtualFile, moduleRef: Ref<Module>?) {
    if (project == null || project.pythonSdk != null) {
      return
    }
    val module = ModuleManager.getInstance(project).modules.firstOrNull() ?: return
    val existingSdks = ProjectSdksModel().apply { reset(project) }.sdks.filter { it.sdkType is PythonSdkType }

    findExistingAssociatedSdk(module, existingSdks)?.let {
      SdkConfigurationUtil.setDirectoryProjectSdk(project, it)
      return
    }

    findDetectedAssociatedEnvironment(module, existingSdks)?.let {
      val newSdk = it.setupAssociated(existingSdks, module.basePath) ?: return
      SdkConfigurationUtil.addSdk(newSdk)
      newSdk.associateWithModule(module, false)
      SdkConfigurationUtil.setDirectoryProjectSdk(project, newSdk)
      return
    }

    // TODO: Introduce an extension for configuring a project via a Python SDK provider
    detectAndSetupPipEnv(project, module, existingSdks)?.let {
      SdkConfigurationUtil.addSdk(it)
      SdkConfigurationUtil.setDirectoryProjectSdk(project, it)
      return
    }

    findExistingSystemWideSdk(existingSdks)?.let {
      SdkConfigurationUtil.setDirectoryProjectSdk(project, it)
      return
    }

    findDetectedSystemWideSdk(existingSdks)?.let {
      SdkConfigurationUtil.createAndAddSDK(it.homePath, PythonSdkType.getInstance())?.apply {
        SdkConfigurationUtil.setDirectoryProjectSdk(project, this)
      }
    }

    SdkConfigurationUtil.configureDirectoryProjectSdk(project, PreferredSdkComparator.INSTANCE, PythonSdkType.getInstance())
  }
}