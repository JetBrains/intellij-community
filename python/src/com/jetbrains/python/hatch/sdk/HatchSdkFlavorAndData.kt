// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.hatch.sdk

import com.intellij.python.hatch.icons.PythonHatchIcons
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.flavors.*
import org.jdom.Element
import java.nio.file.Path
import javax.swing.Icon

typealias HatchSdkFlavorData = PyFlavorData.Empty

internal object HatchSdkFlavor : CPythonSdkFlavor<HatchSdkFlavorData>() {
  override fun getIcon(): Icon = PythonHatchIcons.Logo
  override fun getFlavorDataClass(): Class<HatchSdkFlavorData> = HatchSdkFlavorData::class.java
  override fun isValidSdkPath(pathStr: String): Boolean = false
  override fun isPlatformIndependent(): Boolean = true
}

internal class HatchSdkFlavorProvider : PythonFlavorProvider {
  override fun getFlavor(): PythonSdkFlavor<*> = HatchSdkFlavor
}

class HatchSdkAdditionalData(
  val hatchWorkingDirectory: Path?,
  val hatchEnvironmentName: String?,
) : PythonSdkAdditionalData(PyFlavorAndData(data = HatchSdkFlavorData, flavor = HatchSdkFlavor)) {

  override fun save(element: Element) {
    super.save(element)
    element.setAttribute(IS_HATCH, "true")
    hatchWorkingDirectory?.let {
      element.setAttribute(HATCH_WORKING_DIRECTORY, it.toString())
    }
    hatchEnvironmentName?.let {
      element.setAttribute(HATCH_ENVIRONMENT_NAME, it)
    }
  }

  companion object {
    private const val IS_HATCH = "IS_HATCH"
    private const val HATCH_WORKING_DIRECTORY = "HATCH_WORKING_DIR"
    private const val HATCH_ENVIRONMENT_NAME = "HATCH_ENVIRONMENT_NAME"

    fun createIfHatch(element: Element): HatchSdkAdditionalData? {
      if (element.getAttributeValue(IS_HATCH) != "true") return null

      val data = HatchSdkAdditionalData(
        hatchWorkingDirectory = element.getAttributeValue(HATCH_WORKING_DIRECTORY)?.let { Path.of(it) },
        hatchEnvironmentName = element.getAttributeValue(HATCH_ENVIRONMENT_NAME)
      ).apply {
        load(element)
      }

      return data
    }
  }
}
