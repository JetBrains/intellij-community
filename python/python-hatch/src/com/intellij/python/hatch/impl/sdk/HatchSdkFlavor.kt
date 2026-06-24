package com.intellij.python.hatch.impl.sdk

import com.intellij.python.hatch.icons.PythonHatchIcons
import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor
import com.jetbrains.python.sdk.flavors.PyFlavorData
import java.nio.file.Path
import javax.swing.Icon

typealias HatchSdkFlavorData = PyFlavorData.Empty
internal object HatchSdkFlavor : CPythonSdkFlavor<HatchSdkFlavorData>() {
  override fun getIcon(): Icon = PythonHatchIcons.Logo
  override fun getFlavorDataClass(): Class<HatchSdkFlavorData> = HatchSdkFlavorData::class.java
  override fun isValidSdkPath(pythonBinaryPath: Path): Boolean = false
  override fun isPlatformIndependent(): Boolean = true
}