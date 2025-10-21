// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.sdk.configuration.CreateSdkInfo
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import org.jetbrains.annotations.ApiStatus

/**
 * To be used by [PyInterpreterInspection] only
 */
@ApiStatus.Internal
@RequiresBackgroundThread
internal fun findAllSortedForModuleForJvm(module: Module): List<CreateSdkInfo> = runBlockingMaybeCancellable {
  PyProjectSdkConfigurationExtension.findAllSortedForModule(module)
}
