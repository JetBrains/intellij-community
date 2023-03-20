// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.configuration

import com.intellij.execution.ExecutionException
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.sdk.*

object PyProjectVirtualEnvConfiguration {
  @RequiresEdt
  fun createVirtualEnvSynchronously(baseSdk: Sdk?,
                                    existingSdks: List<Sdk>,
                                    venvRoot: String,
                                    projectBasePath: String?,
                                    project: Project?,
                                    module: Module?,
                                    context: UserDataHolder = UserDataHolderBase(),
                                    inheritSitePackages: Boolean = false,
                                    makeShared: Boolean = false): Sdk? {
    val installedSdk = installSdkIfNeeded(baseSdk, module, existingSdks, context)
    if (installedSdk == null) return null

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
        val packageManager = PyPackageManager.getInstance(sdk)
        return packageManager.createVirtualEnv(venvRoot, inheritSitePackages)
      }
    }
    val associatedPath = if (!makeShared) projectPath else null
    val venvSdk = createSdkByGenerateTask(task, existingSdks, installedSdk, associatedPath, null) ?: return null
    if (!makeShared) {
      venvSdk.associateWithModule(module, projectBasePath)
    }
    project.excludeInnerVirtualEnv(venvSdk)
    PySdkSettings.instance.onVirtualEnvCreated(installedSdk, FileUtil.toSystemIndependentName(venvRoot), projectPath)
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
}