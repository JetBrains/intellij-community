// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.readOnly

import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.flavors.UnixPythonSdkFlavor
import com.jetbrains.python.sdk.sdkFlavor

class PythonSystemSdkReadOnlyProvider : PythonSdkReadOnlyProvider {
  override fun isSdkReadOnly(sdk: Sdk): Boolean {
    return sdk.sdkFlavor is UnixPythonSdkFlavor
  }

  override fun getSdkReadOnlyMessage(sdk: Sdk): String {
    return PyBundle.message("read.only.python.sdk.system.wide.read.only.message")
  }
}