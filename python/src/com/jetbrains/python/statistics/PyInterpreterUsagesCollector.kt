// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.statistics

import com.intellij.internal.statistic.beans.UsageDescriptor
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.pipenv.isPipEnv

/**
 * Reports versions line 35.condaenv.Remote_Vagrant
 */
object PyInterpreterUsagesCollector : ProjectUsagesCollector() {
  override fun getUsages(project: Project) =
    project.sdks
      .map { sdk -> UsageDescriptor(listOf(sdk.version, sdk.executionType, sdk.interpreterType).joinToString(".")) }.toSet()

  override fun getGroupId() = "python.interpreter"
}

object PyInterpreterVersionUsagesCollector : ProjectUsagesCollector() {
  override fun getUsages(project: Project) =
    project.sdks.map { sdk -> UsageDescriptor(sdk.version) }.toSet()

  override fun getGroupId() = "python.interpreter.version"
}

object PyInterpreterTypeUsagesCollector : ProjectUsagesCollector() {
  override fun getUsages(project: Project) =
    project.sdks.map { sdk -> UsageDescriptor(sdk.interpreterType) }.toSet()

  override fun getGroupId() = "python.interpreter.type"
}

object PyInterpreterRemoteUsagesCollector : ProjectUsagesCollector() {
  override fun getUsages(project: Project) =
    project.sdks.map { sdk -> UsageDescriptor(sdk.executionType) }.toSet()

  override fun getGroupId() = "python.interpreter.remote"

  override fun getVersion(): Int = 2
}

private val Project.sdks get() = ModuleManager.getInstance(this).modules.mapNotNull(PythonSdkType::findPythonSdk)
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