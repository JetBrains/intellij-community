// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.execution.target.getTargetType
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.remote.PyRemoteSdkAdditionalData
import com.jetbrains.python.run.target.ConnectionCredentialsToTargetConfigurationConverter
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.flavors.UnixPythonSdkFlavor
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
  else if (sdkAdditionalData is PyRemoteSdkAdditionalData) {
    val convertedSdkAdditionalData = sdkAdditionalData.convertToTargetAwareAdditionalData()
    if (convertedSdkAdditionalData != null) {
      createPythonInterpreterConfigurable(project, sdk, convertedSdkAdditionalData, parentConfigurable)
    }
    else {
      UnsupportedPythonInterpreterConfigurable(sdk)
    }
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
                                                          PythonLanguageRuntimeType.getInstance(),
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

private fun PyRemoteSdkAdditionalData.convertToTargetAwareAdditionalData(): PyTargetAwareAdditionalData? {
  val connectionCredentials = connectionCredentials()
  val targetEnvironmentConfiguration = ConnectionCredentialsToTargetConfigurationConverter.EP_NAME.extensionList.firstNotNullOfOrNull {
    it.tryConvert(connectionCredentials)
  }
  if (targetEnvironmentConfiguration == null) return null
  val targetAwareAdditionalData = PyTargetAwareAdditionalData(flavorAndData)
  targetAwareAdditionalData.targetEnvironmentConfiguration = targetEnvironmentConfiguration
  targetAwareAdditionalData.interpreterPath = this.interpreterPath
  return targetAwareAdditionalData
}