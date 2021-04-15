// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run

import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PythonInterpreterTargetEnvironmentFactory {
  fun getTargetEnvironmentRequest(sdk: Sdk): TargetEnvironmentRequest?

  companion object {
    @JvmStatic
    val EP_NAME = ExtensionPointName<PythonInterpreterTargetEnvironmentFactory>("Pythonid.interpreterTargetEnvironmentFactory")

    @JvmStatic
    fun findTargetEnvironmentRequest(sdk: Sdk): TargetEnvironmentRequest? {
      if (sdk.sdkAdditionalData !is PyRemoteSdkAdditionalDataBase) return LocalTargetEnvironmentRequest()

      return EP_NAME.extensionList.mapNotNull { it.getTargetEnvironmentRequest(sdk) }.firstOrNull()
    }
  }
}