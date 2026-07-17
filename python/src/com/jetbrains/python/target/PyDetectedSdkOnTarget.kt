// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.target

import com.intellij.execution.target.TargetBasedSdkAdditionalData
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.saveTargetConfiguration
import com.jetbrains.python.sdk.PyDetectedSdk
import com.jetbrains.python.sdk.PyRemoteSdkAdditionalDataMarker
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import org.jdom.Element
import java.nio.file.Path

/**
 * Allows passing SDK with such additional data [com.jetbrains.python.sdk.legacy.PythonSdkUtil.isRemote] check.
 *
 * This class is meant for use in UI and should be used with caution.
 */
internal class PyDetectedSdkAdditionalData(override var targetEnvironmentConfiguration: TargetEnvironmentConfiguration?,
                                   flavor: PythonSdkFlavor<*>?) : PythonSdkAdditionalData(createFlavorAndData(flavor), Path.of("")),
                                                                  TargetBasedSdkAdditionalData,
                                                                  PyRemoteSdkAdditionalDataMarker {

  override fun save(rootElement: Element) {
    super.save(rootElement)
    rootElement.setAttribute(PY_DETECTED_SDK_MARKER, "true")
    if (targetEnvironmentConfiguration != null) saveTargetConfiguration(rootElement, targetEnvironmentConfiguration)
  }

  companion object {
    const val PY_DETECTED_SDK_MARKER = "IS_DETECTED"
  }
}

private fun createFlavorAndData(flavor: PythonSdkFlavor<*>?): PyFlavorAndData<*, *> {
  val actualFlavor = flavor ?: PythonSdkFlavor.UnknownFlavor.INSTANCE
  if (!actualFlavor.supportsEmptyData()) {
    throw IllegalArgumentException(actualFlavor.name + " can't be created without additional data")
  }

  @Suppress("UNCHECKED_CAST")
  return PyFlavorAndData(PyFlavorData.Empty, actualFlavor as PythonSdkFlavor<PyFlavorData.Empty>)
}

@Deprecated("Will be dropped soon along with PyDetectedSDK, do not use")
internal fun createDetectedSdk(name: String,
                               targetEnvironmentConfiguration: TargetEnvironmentConfiguration?,
                               flavor: PythonSdkFlavor<*>? = null): PyDetectedSdk {
  val detectedSdk = PyDetectedSdk(name)
  with(detectedSdk.sdkModificator) {
    sdkAdditionalData = PyDetectedSdkAdditionalData(targetEnvironmentConfiguration, flavor)
    applyChangesWithoutWriteAction()
  }
  return detectedSdk
}
