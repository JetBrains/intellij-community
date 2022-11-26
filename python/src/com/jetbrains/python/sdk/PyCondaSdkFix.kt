// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.execution.target.FullPathOnTarget
import com.jetbrains.python.packaging.PyCondaPackageService
import com.jetbrains.python.run.isTargetBased
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.conda.CondaEnvSdkFlavor
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import com.jetbrains.python.sdk.flavors.conda.PyCondaFlavorData
import kotlin.io.path.pathString


/**
 * Temporary hack to convert legacy conda SDK to the new API.
 * Given that [sdk] is local python SDK with ``homePath`` pointing to some ``python`` inside of conda env, we fix [additionalData]
 */
@Suppress("DEPRECATION")
@JvmOverloads
internal fun fixPythonCondaSdk(sdk: Sdk, additionalData: SdkAdditionalData, suggestedCondaPath: FullPathOnTarget? = null) {
  if (additionalData !is PythonSdkAdditionalData) return
  if (sdk.isTargetBased) return
  if (additionalData.flavor is CondaEnvSdkFlavor) return
  val condaPath = suggestedCondaPath ?: PyCondaPackageService.getCondaExecutable(sdk.homePath) ?: return
  val envPath = PythonSdkUtil.findCondaMeta(sdk.homePath)?.parent?.toNioPath()?.pathString ?: return

  val env = PyCondaEnv(PyCondaEnvIdentity.UnnamedEnv(envPath, isBase = condaPath.startsWith(envPath)), condaPath)
  additionalData.changeFlavorAndData(PyFlavorAndData(PyCondaFlavorData(env), CondaEnvSdkFlavor.getInstance()))


}
