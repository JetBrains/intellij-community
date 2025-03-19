// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration

import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.intellij.python.hatch.getHatchService
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.getOrNull
import com.jetbrains.python.orLogException
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.hatch.createSdk

internal class PyHatchSdkConfiguration : PyProjectSdkConfigurationExtension {
  companion object {
    private val LOGGER = Logger.getInstance(PyHatchSdkConfiguration::class.java)
  }

  @RequiresBackgroundThread
  override fun getIntention(module: Module): @IntentionName String? {
    val isReadyAndHaveOwnership = runWithModalProgressBlocking(
      module.project,
      PyCharmCommunityCustomizationBundle.message("sdk.set.up.hatch.project.analysis")
    ) {
      val hatchService = module.getHatchService().getOr { return@runWithModalProgressBlocking false }
      hatchService.isHatchManagedProject().getOrNull() == true
    }

    val intention = when {
      isReadyAndHaveOwnership -> PyCharmCommunityCustomizationBundle.message("sdk.set.up.hatch.environment")
      else -> null
    }
    return intention
  }

  private fun createSdk(module: Module): Sdk? {
    val sdk = runBlockingCancellable {
      val hatchService = module.getHatchService().orLogException(LOGGER)
      val environment = hatchService?.createVirtualEnvironment()?.orLogException(LOGGER)
      environment?.createSdk(module)?.orLogException(LOGGER)
    }?.also {
      SdkConfigurationUtil.addSdk(it)
    }
    return sdk
  }

  @RequiresBackgroundThread
  override fun createAndAddSdkForConfigurator(module: Module): Sdk? = createSdk(module)

  @RequiresBackgroundThread
  override fun createAndAddSdkForInspection(module: Module): Sdk? = createSdk(module)

  override fun supportsHeadlessModel(): Boolean = true
}