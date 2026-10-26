// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.flavors.conda

import com.jetbrains.python.sdk.add.v2.PathHolder
import com.intellij.python.community.impl.conda.CondaPyTool
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.UserDataHolder
import com.intellij.python.community.impl.conda.common.icons.PythonCommunityImplCondaCommonIcons
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PythonBinary
import com.intellij.python.community.impl.conda.environment.CondaEnvironment
import com.intellij.python.sdk.backend.detectPythonEnvironment
import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor
import com.jetbrains.python.sdk.flavors.PythonFlavorProvider
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import java.nio.file.Path
import javax.swing.Icon


internal object CondaEnvSdkFlavor : CPythonSdkFlavor<PyCondaFlavorData>() {
  override fun getIcon(): Icon = PythonCommunityImplCondaCommonIcons.Anaconda

  override fun getFlavorDataClass(): Class<PyCondaFlavorData> = PyCondaFlavorData::class.java

  override fun getManagerId(): String = CondaPyTool.getInstance().fusId.value

  /**
   * Conda names an environment by its name, or by its directory when it has no name: the readable name of its
   * identity. That is what a conda command addresses the environment by.
   */
  override fun toolEnvRefOf(pythonBinary: PathHolder, data: PyCondaFlavorData): String = data.env.envIdentity.userReadableName

  @RequiresBackgroundThread(generateAssertion = false)
  override fun suggestLocalHomePathsImpl(module: Module?, context: UserDataHolder?): MutableCollection<Path> {
    // There is no such thing as "conda homepath" since conda doesn't store python path
    return mutableListOf()
  }

  override fun getUniqueId(): String {
    return "Conda"
  }

  override fun isValidSdkPath(pythonBinaryPath: PythonBinary): Boolean {
    return super.isValidSdkPath(pythonBinaryPath) &&
           pythonBinaryPath.detectPythonEnvironment().successOrNull is CondaEnvironment
  }

  override fun isPlatformIndependent(): Boolean = true

  /**
   * Conda + Colorama doesn't play well with this var, see DS-4036
   */
  override fun providePyCharmHosted(): Boolean = false
}

internal class CondaEnvSdkFlavorProvider : PythonFlavorProvider {
  override fun getFlavor(): PythonSdkFlavor<*> = CondaEnvSdkFlavor
}
