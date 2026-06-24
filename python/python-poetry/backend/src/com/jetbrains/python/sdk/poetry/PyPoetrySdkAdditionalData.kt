// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 *  This source code is edited by @koxudaxi Koudai Aono <koxudaxi@gmail.com>
 */

@ApiStatus.Internal
class PyPoetrySdkAdditionalData : PythonSdkAdditionalData {
  constructor(associatedModulePath: Path?) : super(PyFlavorAndData(PyFlavorData.Empty, PyPoetrySdkFlavor)) {
    this.associatedModulePath = associatedModulePath?.toString()
  }

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