// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.uv

import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.PythonFlavorProvider
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import org.jdom.Element


class UvSdkAdditionalData : PythonSdkAdditionalData {
  constructor() : super(UvSdkFlavor)
  constructor(data: PythonSdkAdditionalData) : super(data)

  override fun save(element: Element) {
    super.save(element)
    element.setAttribute(IS_UV, "true")
  }

  companion object {
    private const val IS_UV = "IS_UV"

    @JvmStatic
    fun load(element: Element): UvSdkAdditionalData? {
      return when {
        element.getAttributeValue(IS_UV) == "true" -> {
          UvSdkAdditionalData().apply {
            load(element)
          }
        }
        else -> null
      }
    }

    @JvmStatic
    fun copy(data: PythonSdkAdditionalData): UvSdkAdditionalData {
      return UvSdkAdditionalData(data)
    }
  }
}

object UvSdkFlavor : CPythonSdkFlavor<PyFlavorData.Empty>() {
  override fun getIcon() = UV_ICON
  override fun getFlavorDataClass(): Class<PyFlavorData.Empty> = PyFlavorData.Empty::class.java

  override fun isValidSdkPath(pathStr: String): Boolean {
    return false
  }
}

class UvSdkFlavorProvider : PythonFlavorProvider {
  override fun getFlavor(platformIndependent: Boolean): PythonSdkFlavor<*> {
    return UvSdkFlavor
  }
}