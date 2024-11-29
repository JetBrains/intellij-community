// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration

import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.uv.PY_PROJECT_TOML
import com.jetbrains.python.sdk.uv.impl.getUvExecutable
import com.jetbrains.python.sdk.uv.setupUvSdkUnderProgress
import java.io.FileNotFoundException
import java.nio.file.Path

class PyUvSdkConfiguration : PyProjectSdkConfigurationExtension {
  companion object {
    private val LOGGER = Logger.getInstance(PyUvSdkConfiguration::class.java)
  }

  @RequiresBackgroundThread
  override fun getIntention(module: Module): @IntentionName String? {
    return findAmongRoots(module, PY_PROJECT_TOML)?.let { toml ->
      getUvExecutable()?.let { PyCharmCommunityCustomizationBundle.message("sdk.set.up.uv.environment", toml.name) }
    }
  }

  @RequiresBackgroundThread
  override fun createAndAddSdkForConfigurator(module: Module): Sdk? {
    return runBlockingCancellable {
      createUv(module).getOrElse {
        LOGGER.warn(it)
        null
      }
    }
  }

  @RequiresBackgroundThread
  override fun createAndAddSdkForInspection(module: Module): Sdk? {
    return runBlockingCancellable {
      createUv(module).getOrElse {
        LOGGER.warn(it)
        null
      }
    }
  }

  override fun supportsHeadlessModel(): Boolean = true

  private suspend fun createUv(module: Module): Result<Sdk> {
    val basePath = module.basePath?.let { Path.of(it) }
    if (basePath == null) {
      return Result.failure(FileNotFoundException("Can't find module base path"))
    }

    val sdk = setupUvSdkUnderProgress(module, basePath, ProjectJdkTable.getInstance().allJdks.toList(), null)
    sdk.onSuccess {
      SdkConfigurationUtil.addSdk(it)
    }

    return sdk
  }
}