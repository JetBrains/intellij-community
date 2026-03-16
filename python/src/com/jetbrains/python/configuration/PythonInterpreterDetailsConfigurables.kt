// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.execution.target.getTargetType
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.target.PythonLanguageRuntimeConfiguration
import com.jetbrains.python.target.PythonLanguageRuntimeType

/**
 * Creates the configurable for Python interpreter SDK based on the type of the given SDKs.
 *
 * @see PythonLocalInterpreterConfigurable
 * @see PythonTargetInterpreterDetailsConfigurable
 */
internal fun createPythonInterpreterConfigurable(project: Project,
                                                 module: Module?,
                                                 sdk: Sdk,
                                                 parentConfigurable: Configurable): Configurable {
  val sdkAdditionalData = sdk.sdkAdditionalData
  return if (!PythonSdkUtil.isRemote(sdk)) {
    PythonLocalInterpreterConfigurable(project, module, sdk)
  }
  else if (sdkAdditionalData is PyTargetAwareAdditionalData) {
    createPythonInterpreterConfigurable(project, sdk, sdkAdditionalData, parentConfigurable)
  }
  else {
    UnsupportedPythonInterpreterConfigurable(sdk)
  }
}

private fun createPythonInterpreterConfigurable(project: Project,
                                                sdk: Sdk,
                                                sdkAdditionalData: PyTargetAwareAdditionalData,
                                                parentConfigurable: Configurable): Configurable {
  // We assume that `targetEnvironmentConfiguration` contains corresponding Python language runtime configuration that is a facade for
  // `sdkAdditionalData.interpreterPath`
  val targetEnvironmentConfiguration = sdkAdditionalData.targetEnvironmentConfiguration
  val targetType: TargetEnvironmentType<TargetEnvironmentConfiguration>? = targetEnvironmentConfiguration?.getTargetType()
  val targetConfigurable = targetType?.createConfigurable(project,
                                                          targetEnvironmentConfiguration,
                                                          PythonLanguageRuntimeType.Helper.getInstance(),
                                                          parentConfigurable)
  return if (targetConfigurable != null) {
    val pythonLanguageRuntimeConfiguration = PythonLanguageRuntimeConfiguration()
    // `sdk.homePath` must not be used for `interpreterPath` as it might contain a credentials prefix (f.e. `docker://`, `ssh://`), when
    // the provided `sdkAdditionalData` is converted from `PyRemoteSdkAdditionalData`
    pythonLanguageRuntimeConfiguration.pythonInterpreterPath = sdkAdditionalData.interpreterPath.orEmpty()
    PythonTargetInterpreterDetailsConfigurable(project,
                                               sdk,
                                               sdkAdditionalData,
                                               targetConfigurable)
  }
  else {
    UnsupportedPythonInterpreterConfigurable(sdk)
  }
}

