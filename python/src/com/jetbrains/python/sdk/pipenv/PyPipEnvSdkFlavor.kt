// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.pipenv

import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor
import java.io.File

/**
 * @author vlan
 */
object PyPipEnvSdkFlavor : CPythonSdkFlavor() {
  override fun getIcon() = PIPENV_ICON

  override fun isValidSdkPath(file: File) = false
}