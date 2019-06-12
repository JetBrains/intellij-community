// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.statistics

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.extensions.getSdk
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.pipenv.isPipEnv

val Project.modules get() = ModuleManager.getInstance(this).modules
val Project.sdks get() = modules.mapNotNull(Module::getSdk)

/**
 * Adds python language and interpreter version if module has sdk
 */
fun FeatureUsageData.addPythonSpecificInfo(module: Module) =
  module.getSdk()?.let { sdk -> addPythonSpecificInfo(sdk) } ?: this

/**
 * Adds python language and interpreter version
 */
fun FeatureUsageData.addPythonSpecificInfo(sdk: Sdk) = addLanguage(PythonLanguage.INSTANCE)
  .addData("python_version", sdk.version)
  .addData("executionType", sdk.executionType)
  .addData("interpreterType", sdk.interpreterType)


private val Sdk.version get() = PythonSdkType.getLanguageLevelForSdk(this).version.toString()
private val Sdk.executionType get(): String = (sdkAdditionalData as? PyRemoteSdkAdditionalDataBase)?.executionType ?: "local"
private val Sdk.interpreterType
  get() = when {
    // The order of checks is important here since e.g. a pipenv is a virtualenv
    isPipEnv -> "pipenv"
    PythonSdkType.isConda(this) -> "condavenv"
    PythonSdkType.isVirtualEnv(this) -> "virtualenv"
    else -> "regular"
  }

private val PyRemoteSdkAdditionalDataBase.executionType: String
  get() = remoteConnectionType.let { type ->
    when {
      type == null -> "Remote_null"
      getPluginInfo(type.javaClass).isDevelopedByJetBrains() -> "Remote_${type.name?.replace(' ', '_')}"
      else -> "third_party"
    }
  }