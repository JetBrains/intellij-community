// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration

import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.intellij.python.hatch.getHatchService
import com.jetbrains.python.getOrNull
import com.jetbrains.python.orLogException
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.hatch.createSdk
import com.jetbrains.python.util.runWithModalBlockingOrInBackground

internal class PyHatchSdkConfiguration : PyProjectSdkConfigurationExtension {
  companion object {
    private val LOGGER = Logger.getInstance(PyHatchSdkConfiguration::class.java)
  }

  override fun getIntention(module: Module): @IntentionName String? {
    val isReadyAndHaveOwnership = runWithModalBlockingOrInBackground(
      project = module.project,
      msg = PyCharmCommunityCustomizationBundle.message("sdk.set.up.hatch.project.analysis")
    ) {
      val hatchService = module.getHatchService().getOr { return@runWithModalBlockingOrInBackground false }
      hatchService.isHatchManagedProject().getOrNull() == true
    }

    val intention = when {
      isReadyAndHaveOwnership -> PyCharmCommunityCustomizationBundle.message("sdk.set.up.hatch.environment")
      else -> null
    }
    return intention
  }

  private fun createSdk(module: Module): Sdk? = runWithModalBlockingOrInBackground(
    project = module.project,
    msg = PyCharmCommunityCustomizationBundle.message("sdk.set.up.hatch.environment")
  ) {
    val hatchService = module.getHatchService().orLogException(LOGGER)
    val environment = hatchService?.createVirtualEnvironment()?.orLogException(LOGGER)
    val sdk = environment?.createSdk(module)?.orLogException(LOGGER)
    sdk
  }

  override fun createAndAddSdkForConfigurator(module: Module): Sdk? = createSdk(module)

  override fun createAndAddSdkForInspection(module: Module): Sdk? = createSdk(module)

  override fun supportsHeadlessModel(): Boolean = true
}