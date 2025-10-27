// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.python.sdkConfigurator.common.detectSdkForModulesIn
import com.intellij.python.sdkConfigurator.common.enableSDKAutoConfigurator
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.sdk.configuration.CreateSdkInfoWithTool
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

/**
 * To be used by [PyInterpreterInspection] only
 */
@ApiStatus.Internal
internal fun detectSdkForModulesForJvmIn(project: Project): Boolean {
  if (!enableSDKAutoConfigurator) {
    return false
  }
  project.service<MyService>().scope.launch {
    detectSdkForModulesIn(project)
  }
  return true
}

@Service(Service.Level.PROJECT)
private class MyService(val scope: CoroutineScope)

/**
 * To be used by [PyInterpreterInspection] only
 */
@ApiStatus.Internal
@RequiresBackgroundThread
internal fun findAllSortedForModuleForJvm(module: Module): List<CreateSdkInfoWithTool> = runBlockingMaybeCancellable {
  PyProjectSdkConfigurationExtension.findAllSortedForModule(module)
}