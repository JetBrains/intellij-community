// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PyProjectVirtualEnvConfiguration")

package com.jetbrains.python.sdk.configuration

import com.intellij.execution.ExecutionException
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.writeAction
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
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.remote.RemoteSdkException
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.Result
import com.jetbrains.python.packaging.PyPackageManagers
import com.jetbrains.python.packaging.PyTargetEnvCreationManager
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor
import com.jetbrains.python.sdk.impl.PySdkBundle
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.target.getInterpreterVersion
import com.jetbrains.python.target.ui.TargetPanelExtension
import com.jetbrains.python.ui.pyModalBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

  val projectPath = projectBasePath ?: module?.baseDir?.path ?: project?.basePath
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
        return PyTargetEnvCreationManager(sdk).createVirtualEnv(venvRoot, inheritSitePackages)
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
      runWithModalProgressBlocking(ModalTaskOwner.guess(), "...") {
        createSdkForTarget(project, it, homePath, existingSdks, targetPanelExtension)
      }
    }
  }

  if (!makeShared) {
    when {
      module != null -> pyModalBlocking { venvSdk.setAssociationToModule(module) }
      projectPath != null -> pyModalBlocking { venvSdk.setAssociationToPath(projectPath) }
    }
  }

  project?.excludeInnerVirtualEnv(venvSdk)
  if (targetEnvironmentConfiguration == null) {
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

internal suspend fun createSdkForTarget(
  project: Project?,
  environmentConfiguration: TargetEnvironmentConfiguration,
  interpreterPath: String,
  existingSdks: Collection<Sdk>,
  targetPanelExtension: TargetPanelExtension?,
  sdkName: String? = null,
): Sdk = withContext(Dispatchers.IO) {
  // TODO [targets] Should flavor be more flexible?
  val data = PyTargetAwareAdditionalData(PyFlavorAndData(PyFlavorData.Empty, VirtualEnvSdkFlavor.getInstance())).also {
    it.interpreterPath = interpreterPath
    it.targetEnvironmentConfiguration = environmentConfiguration
    targetPanelExtension?.applyToAdditionalData(it)
  }

  val sdkVersion = when (val r = data.getInterpreterVersion()) {
    is Result.Success -> r.result.toPythonVersion()
    is Result.Failure -> throw RemoteSdkException(r.error.message) // TODO: Return error instead to show it to user
  }

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
      sdk.setAssociationToModuleAsync(it)
    }
  }

  sdk.sdkModificator.let { modifiableSdk ->
    modifiableSdk.versionString = sdkVersion
    writeAction {
      modifiableSdk.commitChanges()
    }
  }

  // FIXME: should we persist it?
  data.isValid = true

  return@withContext sdk
}