// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("PyProjectVirtualEnvConfiguration")

package com.jetbrains.python.sdk.configuration

import com.intellij.execution.ExecutionException
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.packaging.PyPackageManagers
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.add.v1.PyAddSdkPanelBase
import com.jetbrains.python.sdk.add.v1.PyAddSdkPanelBase.Companion.isLocal
import com.jetbrains.python.sdk.add.v1.TargetPanelExtension
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.target.getInterpreterVersion
import org.jetbrains.annotations.ApiStatus

/**
 * Use [com.jetbrains.python.projectCreation.createVenvAndSdk] unless you need the Targets API.
 *
 * If you need venv only, please use [com.intellij.python.community.impl.venv.createVenv]: it is cleaner and suspend.
 */
@ApiStatus.Internal
@RequiresEdt
fun createVirtualEnvAndSdkSynchronously(
  baseSdk: Sdk,
  existingSdks: List<Sdk>,
  venvRoot: String,
  projectBasePath: String?,
  project: Project?,
  module: Module?,
  context: UserDataHolder = UserDataHolderBase(),
  inheritSitePackages: Boolean = false,
  makeShared: Boolean = false,
  targetPanelExtension: TargetPanelExtension? = null,
): Sdk {
  val targetEnvironmentConfiguration = baseSdk.targetEnvConfiguration
  val installedSdk: Sdk = if (targetEnvironmentConfiguration == null) {
    installSdkIfNeeded(baseSdk, module, existingSdks, context).getOrThrow()
  }
  else {
    baseSdk
  }

  val projectPath = projectBasePath ?: module?.basePath ?: project?.basePath
  val task = object : Task.WithResult<String, ExecutionException>(project, PySdkBundle.message("python.creating.venv.title"), false) {
    override fun compute(indicator: ProgressIndicator): String {
      indicator.isIndeterminate = true
      val sdk = if (installedSdk is Disposable && Disposer.isDisposed(installedSdk)) {
        ProjectJdkTable.getInstance().findJdk(installedSdk.name)!!
      }
      else {
        installedSdk
      }

      try {
        val packageManager = PyPackageManager.getInstance(sdk)
        return packageManager.createVirtualEnv(venvRoot, inheritSitePackages)
      }
      finally {
        PyPackageManagers.getInstance().clearCache(sdk)
      }
    }
  }
  val associatedPath = if (!makeShared) projectPath else null
  val venvSdk = targetEnvironmentConfiguration.let {
    if (it == null) {
      // here is the local machine case
      createSdkByGenerateTask(task, existingSdks, installedSdk, associatedPath, null)
    }
    else {
      val homePath = ProgressManager.getInstance().run(task)
      createSdkForTarget(project, it, homePath, existingSdks, targetPanelExtension)
    }
  }

  if (!makeShared) {
    when {
      module != null -> venvSdk.setAssociationToModule(module)
      projectPath != null -> venvSdk.setAssociationToPath(projectPath)
    }
  }

  project?.excludeInnerVirtualEnv(venvSdk)
  if (targetEnvironmentConfiguration.isLocal()) {
    // The method `onVirtualEnvCreated(..)` stores preferred base path to virtual envs. Storing here the base path from the non-local
    // target (e.g. a path from SSH machine or a Docker image) ends up with a meaningless default for the local machine.
    // If we would like to store preferred paths for non-local targets we need to use some key to identify the exact target.
    PySdkSettings.instance.onVirtualEnvCreated(installedSdk.homePath, FileUtil.toSystemIndependentName(venvRoot), projectPath)
  }

  return venvSdk
}

fun findPreferredVirtualEnvBaseSdk(existingBaseSdks: List<Sdk>): Sdk? {
  val preferredSdkPath = PySdkSettings.instance.preferredVirtualEnvBaseSdk.takeIf(FileUtil::exists)
  val detectedPreferredSdk = existingBaseSdks.find { it.homePath == preferredSdkPath }
  return when {
    detectedPreferredSdk != null -> detectedPreferredSdk
    preferredSdkPath != null -> PyDetectedSdk(preferredSdkPath)
    else -> existingBaseSdks.getOrNull(0)
  }
}

internal fun createSdkForTarget(
  project: Project?,
  environmentConfiguration: TargetEnvironmentConfiguration,
  interpreterPath: String,
  existingSdks: Collection<Sdk>,
  targetPanelExtension: TargetPanelExtension?,
  sdkName: String? = null,
): Sdk {
  // TODO [targets] Should flavor be more flexible?
  val data = PyTargetAwareAdditionalData(PyFlavorAndData(PyFlavorData.Empty, PyAddSdkPanelBase.virtualEnvSdkFlavor)).also {
    it.interpreterPath = interpreterPath
    it.targetEnvironmentConfiguration = environmentConfiguration
    targetPanelExtension?.applyToAdditionalData(it)
  }

  val sdkVersion: String? = data.getInterpreterVersion(project, interpreterPath)

  val name = if (!sdkName.isNullOrEmpty()) {
    sdkName
  }
  else {
    PythonInterpreterTargetEnvironmentFactory.findDefaultSdkName(project, data, sdkVersion)
  }

  val sdk = SdkConfigurationUtil.createSdk(existingSdks, interpreterPath, PythonSdkType.getInstance(), data, name)
  if (PythonInterpreterTargetEnvironmentFactory.by(environmentConfiguration)?.needAssociateWithModule() == true) {
    // FIXME: multi module project support
    project?.modules?.firstOrNull()?.let {
      sdk.setAssociationToModule(it)
    }
  }

  sdk.sdkModificator.let { modifiableSdk ->
    modifiableSdk.versionString = sdkVersion
    ApplicationManager.getApplication().runWriteAction {
      modifiableSdk.commitChanges()
    }
  }

  // FIXME: should we persist it?
  data.isValid = true

  return sdk
}