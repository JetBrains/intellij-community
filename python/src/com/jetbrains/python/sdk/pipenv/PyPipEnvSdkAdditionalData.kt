// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.pipenv

import com.jetbrains.python.sdk.PythonSdkAdditionalData
import org.jdom.Element

/**
 * Additional Pipenv data associated with an SDK.
 *
 * @author vlan
 */
class PyPipEnvSdkAdditionalData : PythonSdkAdditionalData {
  constructor() : super(PyPipEnvSdkFlavor)
  constructor(data: PythonSdkAdditionalData) : super(data)

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
          PyPipEnvSdkAdditionalData().apply {
            load(element)
          }
        }
        else -> null
      }

    /**
     * Creates a new instance of data with copied fields.
     */
    @JvmStatic
    fun copy(data: PythonSdkAdditionalData): PyPipEnvSdkAdditionalData =
      PyPipEnvSdkAdditionalData(data)
  }
}