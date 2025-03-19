// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.hatch

import com.intellij.python.hatch.icons.PythonHatchIcons
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.flavors.*
import org.jdom.Element
import javax.swing.Icon


typealias HatchSdkFlavorData = PyFlavorData.Empty

object HatchSdkFlavor : CPythonSdkFlavor<HatchSdkFlavorData>() {
  override fun getIcon(): Icon = PythonHatchIcons.Logo
  override fun getFlavorDataClass(): Class<HatchSdkFlavorData> = HatchSdkFlavorData::class.java
  override fun isValidSdkPath(pathStr: String): Boolean = false
  override fun isPlatformIndependent(): Boolean = true
}

class HatchSdkFlavorProvider : PythonFlavorProvider {
  override fun getFlavor(platformIndependent: Boolean): PythonSdkFlavor<*> = HatchSdkFlavor
}

class HatchSdkAdditionalData(data: PythonSdkAdditionalData) : PythonSdkAdditionalData(data) {
  constructor() : this(
    data = PythonSdkAdditionalData(PyFlavorAndData(data = HatchSdkFlavorData, flavor = HatchSdkFlavor))
  )

  override fun save(element: Element) {
    super.save(element)
    element.setAttribute(IS_HATCH, "true")
  }

  companion object {
    private const val IS_HATCH = "IS_HATCH"
  }
}
