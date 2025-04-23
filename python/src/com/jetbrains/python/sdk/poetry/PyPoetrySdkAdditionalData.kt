// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.poetry

import com.jetbrains.python.sdk.PythonSdkAdditionalData
import org.jdom.Element
import java.nio.file.Path

/**
 *  This source code is edited by @koxudaxi Koudai Aono <koxudaxi@gmail.com>
 */

class PyPoetrySdkAdditionalData : PythonSdkAdditionalData {
  constructor(associatedModulePath: Path?) : super(PyPoetrySdkFlavor) {
    this.associatedModulePath = associatedModulePath?.toString()
  }

  constructor(data: PythonSdkAdditionalData) : super(data)

  override fun save(element: Element) {
    super.save(element)
    // We use this flag to create an instance of the correct additional data class. The flag itself is not used after that
    element.setAttribute(IS_POETRY, "true")
  }

  companion object {
    private const val IS_POETRY = "IS_POETRY"

    /**
     * Loads serialized data from an XML element.
     */
    @JvmStatic
    fun load(element: Element): PyPoetrySdkAdditionalData? =
      when {
        element.getAttributeValue(IS_POETRY) == "true" -> {
          PyPoetrySdkAdditionalData(null).apply {
            load(element)
          }
        }
        else -> null
      }
  }
}