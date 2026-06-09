// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.poetry

import com.intellij.python.community.impl.poetry.common.icons.PythonCommunityImplPoetryCommonIcons
import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.PythonFlavorProvider
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import java.nio.file.Path
import javax.swing.Icon


/**
 *  This source code is edited by @koxudaxi Koudai Aono <koxudaxi@gmail.com>
 */

internal object PyPoetrySdkFlavor : CPythonSdkFlavor<PyFlavorData.Empty>() {
  override fun getIcon(): Icon = PythonCommunityImplPoetryCommonIcons.Poetry
  override fun getFlavorDataClass(): Class<PyFlavorData.Empty> = PyFlavorData.Empty::class.java

  override fun isValidSdkPath(pythonBinaryPath: Path): Boolean = false
}

internal class PyPoetrySdkFlavorProvider : PythonFlavorProvider {
  override fun getFlavor(): PythonSdkFlavor<*> = PyPoetrySdkFlavor
}