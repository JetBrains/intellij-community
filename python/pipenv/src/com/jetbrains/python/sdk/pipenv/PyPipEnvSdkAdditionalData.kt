// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.pipenv

import com.jetbrains.python.PyInternalExecApi
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import org.jdom.Element
import java.nio.file.Path

/**
 * Additional Pipenv data associated with an SDK.
 *
 */
@PyInternalExecApi
class PyPipEnvSdkAdditionalData : PythonSdkAdditionalData {
  constructor(workingDirectory: Path) : super(PyFlavorAndData(PyFlavorData.Empty, PyPipEnvSdkFlavor), workingDirectory)

  override fun save(element: Element) {
    super.save(element)
    // We use this flag to create an instance of the correct additional data class. The flag itself is not used after that
    element.setAttribute(IS_PIPENV, "true")
  }

  companion object {
    private const val IS_PIPENV = "IS_PIPENV"

    /**
     * Loads serialized data from an XML element.
     */
    @JvmStatic
    fun load(element: Element): PyPipEnvSdkAdditionalData? =
      when {
        element.getAttributeValue(IS_PIPENV) == "true" -> {
          PyPipEnvSdkAdditionalData(Path.of("")).apply {
            load(element)
          }
        }
        else -> null
      }
  }
}