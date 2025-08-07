// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.readOnly

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.sdk.PythonSdkUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PythonSdkReadOnlyProvider {
  fun isSdkReadOnly(sdk: Sdk): Boolean
  fun getSdkReadOnlyMessage(sdk: Sdk): String

  companion object {
    private val EP_NAME = ExtensionPointName.create<PythonSdkReadOnlyProvider>("Pythonid.pythonSdkReadOnlyProvider")

    fun isReadOnly(sdk: Sdk): Boolean {
      if (!PythonSdkUtil.isPythonSdk(sdk)) {
        return false
      }

      return EP_NAME.extensionList.any { it.isSdkReadOnly(sdk) }
    }

    fun getReadOnlyMessage(sdk: Sdk): String? {
      if (!PythonSdkUtil.isPythonSdk(sdk)) {
        return null
      }

      return EP_NAME.extensionList.firstOrNull { it.isSdkReadOnly(sdk) }?.getSdkReadOnlyMessage(sdk)
    }

  }
}