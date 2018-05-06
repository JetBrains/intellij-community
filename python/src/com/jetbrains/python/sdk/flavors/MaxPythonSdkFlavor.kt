// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.flavors

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import icons.PythonIcons
import java.io.File
import javax.swing.Icon

class MaxSdkFlavor private constructor() : PythonSdkFlavor() {
  override fun isValidSdkHome(path: String?): Boolean {
    val file = File(path)
    return file.isFile && isValidSdkPath(file)
  }

  override fun isValidSdkPath(file: File): Boolean {
    val name = FileUtil.getNameWithoutExtension(file).toLowerCase()
    return name.startsWith("3dsmaxpy")
  }

  override fun getVersionOption(): String {
    return "--version"
  }

  override fun getName(): String {
    return "3dsMaxPy"
  }

  override fun getIcon(): Icon {
    return PythonIcons.Python.Python
  }

  override fun getSdkPath(path: VirtualFile): VirtualFile? {
    return path
  }

  companion object {
    var INSTANCE = MaxSdkFlavor()
  }
}

class MaxFlavorProvider : PythonFlavorProvider {
  override fun getFlavor(platformIndependent: Boolean) = MaxSdkFlavor.INSTANCE
}