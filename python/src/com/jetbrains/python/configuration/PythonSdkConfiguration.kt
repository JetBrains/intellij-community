// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.util.concurrency.ThreadingAssertions
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun SdkModificator.associateWithModule(module: Module) {
  val basePath = module.basePath ?: throw IllegalArgumentException("Module $module has no roots and can't be associated")
  getOrCreateSdkAdditionalData().associatedModulePath = basePath
}

/**
 * Associate Python interpreter SDK with the primary [project]'s module, which is expected to be located in [Project.getBasePath].
 */
internal fun SdkModificator.associateWithProject(project: Project) {
  val projectBasePath = project.basePath
  if (projectBasePath != null) getOrCreateSdkAdditionalData().associatedModulePath = projectBasePath
}

internal fun SdkModificator.resetAssociatedModulePath() {
  (sdkAdditionalData as? PythonSdkAdditionalData)?.associatedModulePath = null
}

private fun SdkModificator.getOrCreateSdkAdditionalData(): PythonSdkAdditionalData {
  ThreadingAssertions.assertEventDispatchThread()
  var additionalData = sdkAdditionalData as? PythonSdkAdditionalData
  if (additionalData == null) {
    additionalData = PythonSdkAdditionalData(PythonSdkFlavor.getFlavor(homePath))
    sdkAdditionalData = additionalData
  }
  return additionalData
}