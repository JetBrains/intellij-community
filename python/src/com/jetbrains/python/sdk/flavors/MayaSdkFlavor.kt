/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.sdk.flavors

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import icons.PythonIcons
import java.io.File
import javax.swing.Icon

/**
 * @author traff
 */
class MayaSdkFlavor private constructor() : PythonSdkFlavor() {

  override fun isValidSdkHome(path: String): Boolean {
    val file = File(path)
    return file.isFile && isValidSdkPath(file) || isMayaFolder(file)
  }

  override fun isValidSdkPath(file: File): Boolean {
    val name = FileUtil.getNameWithoutExtension(file).toLowerCase()
    return name.startsWith("mayapy")
  }

  override fun getVersionOption(): String {
    return "--version"
  }

  override fun getName(): String {
    return "MayaPy"
  }

  override fun getIcon(): Icon {
    return PythonIcons.Python.Python //TODO: maya icon
  }

  override fun getSdkPath(path: VirtualFile): VirtualFile? {
    if (isMayaFolder(File(path.path))) {
      return path.findFileByRelativePath("Contents/bin/mayapy")
    }
    return path
  }

  companion object {

    var INSTANCE: MayaSdkFlavor = MayaSdkFlavor()

    private fun isMayaFolder(file: File): Boolean {
      return file.isDirectory && file.name == "Maya.app"
    }
  }
}


class MayaFlavorProvider: PythonFlavorProvider {
  override fun getFlavor(platformIndependent: Boolean): MayaSdkFlavor = MayaSdkFlavor.INSTANCE
}
