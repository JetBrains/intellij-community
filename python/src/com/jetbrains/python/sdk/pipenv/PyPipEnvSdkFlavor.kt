// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.pipenv

import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor
import com.jetbrains.python.sdk.flavors.PyFlavorData
import java.io.File

/**
 * @author vlan
 */
object PyPipEnvSdkFlavor : CPythonSdkFlavor<PyFlavorData.Empty>() {
  override fun getIcon() = PIPENV_ICON
  override fun getFlavorDataClass(): Class<PyFlavorData.Empty> = PyFlavorData.Empty::class.java

  override fun isValidSdkPath(file: File) = false
}