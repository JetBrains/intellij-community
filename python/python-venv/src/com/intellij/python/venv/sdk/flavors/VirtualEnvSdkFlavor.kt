// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.venv.sdk.flavors

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.python.venv.icons.PythonVenvIcons
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PyInternalExecApi
import com.jetbrains.python.sdk.BASE_DIR
import com.jetbrains.python.sdk.PythonEnvironment
import com.jetbrains.python.sdk.baseDir
import com.jetbrains.python.sdk.detectPythonEnvironment
import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.venvReader.VirtualEnvReader
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import javax.swing.Icon

@Internal
@PyInternalExecApi
class VirtualEnvSdkFlavor private constructor() : CPythonSdkFlavor<PyFlavorData.Empty>() {

  override fun isPlatformIndependent(): Boolean = true

  override fun getFlavorDataClass(): Class<PyFlavorData.Empty> = PyFlavorData.Empty::class.java

  @RequiresBackgroundThread(generateAssertion = false)
  override fun suggestLocalHomePathsImpl(module: Module?, context: UserDataHolder?): Collection<Path> = runReadActionBlocking {
    val candidates = mutableListOf<Path>()

    val baseDirFromModule = module?.baseDir
    val baseDirFromContext = context?.getUserData(BASE_DIR)

    val reader = VirtualEnvReader()
    when {
      baseDirFromModule != null -> candidates.addAll(reader.findVenvsInDir(baseDirFromModule.toNioPath()))
      baseDirFromContext != null -> VfsUtil.findFile(baseDirFromContext, false)?.let {
        candidates.addAll(reader.findVenvsInDir(it.toNioPath()))
      }
    }

    candidates.addAll(reader.findVEnvInterpreters())
    candidates.addAll(reader.findPyenvInterpreters())

    candidates.filter {
      when (it.detectPythonEnvironment().successOrNull) {
        is PythonEnvironment.Venv -> true
        is PythonEnvironment.Conda, is PythonEnvironment.SystemPython, null -> false
      }
    }
  }

  override fun isValidSdkPath(pythonBinaryPath: Path): Boolean {
    if (!super.isValidSdkPath(pythonBinaryPath)) return false
    return when (pythonBinaryPath.detectPythonEnvironment().successOrNull) {
      is PythonEnvironment.Venv -> true
      is PythonEnvironment.Conda, is PythonEnvironment.SystemPython, null -> false
    }
  }

  override fun getIcon(): Icon = PythonVenvIcons.VirtualEnv

  @Suppress("CompanionObjectInExtension")
  companion object {
    @JvmStatic
    fun getInstance(): VirtualEnvSdkFlavor = EP_NAME.findExtension(VirtualEnvSdkFlavor::class.java)!!
  }
}
