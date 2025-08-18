// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.flavors.conda

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.UserDataHolder
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor
import com.jetbrains.python.sdk.flavors.PythonFlavorProvider
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import javax.swing.Icon


@ApiStatus.Internal
object CondaEnvSdkFlavor : CPythonSdkFlavor<PyCondaFlavorData>() {
  override fun getIcon(): Icon = PythonIcons.Python.Anaconda

  override fun getFlavorDataClass(): Class<PyCondaFlavorData> = PyCondaFlavorData::class.java

  @RequiresBackgroundThread(generateAssertion = false)
  override fun suggestLocalHomePathsImpl(module: Module?, context: UserDataHolder?): MutableCollection<Path> {
    // There is no such thing as "conda homepath" since conda doesn't store python path
    return mutableListOf()
  }

  override fun sdkSeemsValid(
    sdk: Sdk,
    flavorData: PyCondaFlavorData,
    targetConfig: TargetEnvironmentConfiguration?,
  ): Boolean {
    val condaPath = flavorData.env.fullCondaPathOnTarget
    return isFileExecutable(condaPath, targetConfig)
  }

  override fun getUniqueId(): String {
    return "Conda"
  }

  override fun isValidSdkPath(pathStr: String): Boolean {
    if (!super.isValidSdkPath(pathStr)) {
      return false
    }

    return PythonSdkUtil.isConda(pathStr)
  }

  override fun isPlatformIndependent(): Boolean = true

  /**
   * Conda + Colorama doesn't play well with this var, see DS-4036
   */
  override fun providePyCharmHosted(): Boolean = false

  override fun supportsEmptyData(): Boolean = false
}

internal class CondaEnvSdkFlavorProvider : PythonFlavorProvider {
  override fun getFlavor(): PythonSdkFlavor<*> = CondaEnvSdkFlavor
}
