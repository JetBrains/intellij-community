// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.pipenv

import com.intellij.python.community.impl.pipenv.PipEnvPyTool
import com.jetbrains.python.PyInternalExecApi
import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.PythonFlavorProvider
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import javax.swing.Icon


@ApiStatus.Internal
@PyInternalExecApi
object PyPipEnvSdkFlavor : CPythonSdkFlavor<PyFlavorData.Empty>() {
  override fun getIcon(): Icon = PipEnvPyTool.getInstance().icon
  override fun getFlavorDataClass(): Class<PyFlavorData.Empty> = PyFlavorData.Empty::class.java

  override fun isValidSdkPath(pythonBinaryPath: Path): Boolean = false
}


internal class PyPipEnvSdkFlavorProvider : PythonFlavorProvider {
  override fun getFlavor(): PyPipEnvSdkFlavor = PyPipEnvSdkFlavor
}
