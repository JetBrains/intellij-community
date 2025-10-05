// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.uv

import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.PythonFlavorProvider
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import org.jdom.Element
import java.nio.file.Path
import javax.swing.Icon
import kotlin.io.path.pathString


class UvSdkAdditionalData : PythonSdkAdditionalData {
  val uvWorkingDirectory: Path?
  val usePip: Boolean

  constructor(uvWorkingDirectory: Path? = null, usePip: Boolean = false) : super(UvSdkFlavor) {
    this.uvWorkingDirectory = uvWorkingDirectory
    this.usePip = usePip
  }

  constructor(data: PythonSdkAdditionalData, uvWorkingDirectory: Path? = null, usePip: Boolean = false) : super(data) {
    this.uvWorkingDirectory = uvWorkingDirectory
    this.usePip = usePip
  }

  override fun save(element: Element) {
    super.save(element)
    element.setAttribute(IS_UV, "true")

    // keep backward compatibility with old data
    if (uvWorkingDirectory?.pathString?.isNotBlank() == true) {
      element.setAttribute(UV_WORKING_DIR, uvWorkingDirectory.pathString)
    }

    if (usePip) {
      element.setAttribute(USE_PIP, usePip.toString())
    }
  }

  companion object {
    private const val IS_UV = "IS_UV"
    private const val UV_WORKING_DIR = "UV_WORKING_DIR"
    private const val USE_PIP = "USE_PIP"

    @JvmStatic
    fun load(element: Element): UvSdkAdditionalData? {
      return when {
        element.getAttributeValue(IS_UV) == "true" -> {
          val uvWorkingDirectory = if (element.getAttributeValue(UV_WORKING_DIR).isNullOrEmpty()) null else Path.of(element.getAttributeValue(UV_WORKING_DIR))
          val usePip = element.getAttributeValue(USE_PIP)?.toBoolean() ?: false
          UvSdkAdditionalData(uvWorkingDirectory, usePip).apply {
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
  override fun getIcon(): Icon = UV_ICON
  override fun getFlavorDataClass(): Class<PyFlavorData.Empty> = PyFlavorData.Empty::class.java

  override fun isValidSdkPath(pathStr: String): Boolean {
    return false
  }
}

class UvSdkFlavorProvider : PythonFlavorProvider {
  override fun getFlavor(): PythonSdkFlavor<*> {
    return UvSdkFlavor
  }
}