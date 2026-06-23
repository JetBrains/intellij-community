package com.jetbrains.python.sdk.uv

import com.intellij.python.uv.common.icons.PythonUvCommonIcons
import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor
import java.nio.file.Path
import javax.swing.Icon

internal object UvSdkFlavor : CPythonSdkFlavor<UvSdkFlavorData>() {
  override fun getIcon(): Icon = PythonUvCommonIcons.UV
  override fun getFlavorDataClass(): Class<UvSdkFlavorData> = UvSdkFlavorData::class.java

  override fun isValidSdkPath(pythonBinaryPath: Path): Boolean {
    return false
  }
}