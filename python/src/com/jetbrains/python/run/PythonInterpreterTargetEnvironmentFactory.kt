// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run

import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase
import com.jetbrains.python.run.target.HelpersAwareLocalTargetEnvironmentRequest
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PythonInterpreterTargetEnvironmentFactory {
  fun getPythonTargetInterpreter(sdk: Sdk, project: Project): HelpersAwareTargetEnvironmentRequest?

  fun getTargetType(): TargetEnvironmentType<*>

  companion object {
    @JvmStatic
    val EP_NAME = ExtensionPointName<PythonInterpreterTargetEnvironmentFactory>("Pythonid.interpreterTargetEnvironmentFactory")

    @JvmStatic
    fun findPythonTargetInterpreter(sdk: Sdk, project: Project): HelpersAwareTargetEnvironmentRequest? =
      when (sdk.sdkAdditionalData) {
        is PyRemoteSdkAdditionalDataBase -> EP_NAME.extensionList.mapNotNull { it.getPythonTargetInterpreter(sdk, project) }.firstOrNull()
        else -> HelpersAwareLocalTargetEnvironmentRequest
      }
  }
}