// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.flavors

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.nio.file.Path

internal class MayaSdkFlavor private constructor() : CPythonSdkFlavor<PyFlavorData.Empty>() {
  override fun getFlavorDataClass(): Class<PyFlavorData.Empty> = PyFlavorData.Empty::class.java

  override fun isValidSdkPath(pythonBinaryPath: Path): Boolean {
    val fileName = pythonBinaryPath.fileName?.toString() ?: return false
    val name = FileUtil.getNameWithoutExtension(fileName).lowercase()
    return name.startsWith("mayapy")
  }

  override fun getSdkPath(path: VirtualFile): VirtualFile? {
    if (isMayaFolder(File(path.path))) {
      return path.findFileByRelativePath("Contents/bin/mayapy")
    }
    return path
  }

  companion object {

    val INSTANCE: MayaSdkFlavor = MayaSdkFlavor()

    private fun isMayaFolder(file: File): Boolean {
      return file.isDirectory && file.name == "Maya.app"
    }
  }
}


internal class MayaFlavorProvider : PythonFlavorProvider {
  override fun getFlavor(): MayaSdkFlavor = MayaSdkFlavor.INSTANCE
}
