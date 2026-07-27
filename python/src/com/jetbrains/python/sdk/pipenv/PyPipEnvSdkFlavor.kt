// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.pipenv

import com.intellij.python.community.impl.pipenv.PipEnvPyTool
import com.jetbrains.python.PyInternalExecApi
import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.PythonFlavorProvider
import java.nio.file.Path
import javax.swing.Icon


@PyInternalExecApi
internal object PyPipEnvSdkFlavor : CPythonSdkFlavor<PyFlavorData.Empty>() {
  override fun getIcon(): Icon = PipEnvPyTool.getInstance().icon
  override fun getFlavorDataClass(): Class<PyFlavorData.Empty> = PyFlavorData.Empty::class.java

  override fun isValidSdkPath(pythonBinaryPath: Path): Boolean = false
}


internal class PyPipEnvSdkFlavorProvider : PythonFlavorProvider {
  override fun getFlavor(): PyPipEnvSdkFlavor = PyPipEnvSdkFlavor
}
