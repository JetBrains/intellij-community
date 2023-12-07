// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add.target

import com.intellij.execution.target.TargetBasedSdkAdditionalData
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.application.ApplicationManager
import com.jetbrains.python.sdk.PyDetectedSdk
import com.jetbrains.python.sdk.PyRemoteSdkAdditionalDataMarker
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor

/**
 * Allows passing SDK with such additional data [com.jetbrains.python.sdk.PythonSdkUtil.isRemote] check.
 *
 * This class is meant for use in UI and should be used with caution.
 */
class PyDetectedSdkAdditionalData(override val targetEnvironmentConfiguration: TargetEnvironmentConfiguration?,
                                  flavor: PythonSdkFlavor<*>?) : PythonSdkAdditionalData(flavor),
                                                                 TargetBasedSdkAdditionalData,
                                                                 PyRemoteSdkAdditionalDataMarker

/**
 * Returns new [PyDetectedSdk] with the additional data that corresponds to the local or non-local interpreter based on the provided flag.
 *
 * @see com.jetbrains.python.sdk.PySdkExtKt.isValid
 */
internal fun createDetectedSdk(name: String, isLocal: Boolean): PyDetectedSdk {
  val sdk = PyDetectedSdk(name)
  if (!isLocal) {
    val sdkModificator = sdk.sdkModificator
    sdkModificator.sdkAdditionalData = PyDetectedSdkAdditionalData(targetEnvironmentConfiguration = null, flavor = null)
    sdkModificator.commitChanges()
  }
  return sdk
}

internal fun createDetectedSdk(name: String,
                               targetEnvironmentConfiguration: TargetEnvironmentConfiguration?,
                               flavor: PythonSdkFlavor<*>? = null): PyDetectedSdk {
  val detectedSdk = PyDetectedSdk(name)
  val sdkModificator = detectedSdk.sdkModificator
  sdkModificator.sdkAdditionalData = PyDetectedSdkAdditionalData(targetEnvironmentConfiguration, flavor)
  val application = ApplicationManager.getApplication()
  application.invokeAndWait {
    application.runWriteAction {
      sdkModificator.commitChanges()
    }
  }
  return detectedSdk
}
