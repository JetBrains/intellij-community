// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run

import com.intellij.execution.target.TargetEnvironmentFactory
import com.intellij.execution.target.local.LocalTargetEnvironmentFactory
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PythonInterpreterTargetEnvironmentFactory {
  fun getTargetEnvironmentFactory(sdk: Sdk): TargetEnvironmentFactory?

  companion object {
    @JvmStatic
    val EP_NAME = ExtensionPointName<PythonInterpreterTargetEnvironmentFactory>("Pythonid.interpreterTargetEnvironmentFactory")

    @JvmStatic
    fun findTargetEnvironmentFactory(sdk: Sdk): TargetEnvironmentFactory? {
      if (sdk.sdkAdditionalData !is PyRemoteSdkAdditionalDataBase) return LocalTargetEnvironmentFactory()

      return EP_NAME.extensionList.mapNotNull { it.getTargetEnvironmentFactory(sdk) }.firstOrNull()
    }
  }
}