// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration

import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.intellij.python.hatch.HatchVirtualEnvironment
import com.intellij.python.hatch.cli.HatchEnvironment
import com.intellij.python.hatch.getHatchService
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrLogException
import com.jetbrains.python.hatch.sdk.createSdk
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.util.runWithModalBlockingOrInBackground

internal class PyHatchSdkConfiguration : PyProjectSdkConfigurationExtension {
  companion object {
    private val LOGGER = Logger.getInstance(PyHatchSdkConfiguration::class.java)
  }

  override suspend fun getIntention(module: Module): @IntentionName String? {
    val isReadyAndHaveOwnership = reportRawProgress {
      it.text(PyCharmCommunityCustomizationBundle.message("sdk.set.up.hatch.project.analysis"))
      val hatchService = module.getHatchService().getOr { return@reportRawProgress false }
      hatchService.isHatchManagedProject().getOrLogException(LOGGER) == true
    }

    val intention = when {
      isReadyAndHaveOwnership -> PyCharmCommunityCustomizationBundle.message("sdk.set.up.hatch.environment")
      else -> null
    }
    return intention
  }

  private fun createSdk(module: Module): PyResult<Sdk> = runWithModalBlockingOrInBackground(
    project = module.project,
    msg = PyCharmCommunityCustomizationBundle.message("sdk.set.up.hatch.environment")
  ) {
    val hatchService = module.getHatchService().getOr { return@runWithModalBlockingOrInBackground it }
    val createdEnvironment = hatchService.createVirtualEnvironment().getOr { return@runWithModalBlockingOrInBackground it }

    val hatchVenv = HatchVirtualEnvironment(HatchEnvironment.DEFAULT, createdEnvironment)
    val sdk = hatchVenv.createSdk(hatchService.getWorkingDirectoryPath(), module)
    sdk
  }
  override suspend fun createAndAddSdkForConfigurator(module: Module): PyResult<Sdk> = createSdk(module)

  override suspend fun createAndAddSdkForInspection(module: Module): PyResult<Sdk> = createSdk(module)

  override fun supportsHeadlessModel(): Boolean = true
}