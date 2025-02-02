// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.testFramework.testEnv

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.utils.vfs.refreshAndGetVirtualFile
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonBinary
import java.nio.file.Path

data object TypeVanillaPython3 : PythonType<PythonBinary>("python3") {
  override suspend fun createSdkFor(python: PythonBinary): Sdk = createSdk(python)

  // TODO: DOC
  fun createSdk(python: PythonBinary): Sdk =
    SdkConfigurationUtil.setupSdk(emptyArray(), python.refreshAndGetVirtualFile(),
                                  SdkType.findByName(PyNames.PYTHON_SDK_ID_NAME)!!, null, null)

  // Python is directly executable
  override suspend fun pythonPathToEnvironment(pythonBinary: PythonBinary, envDir: Path): Pair<PythonBinary, AutoCloseable> {
    val disposable = Disposer.newDisposable("Python tests disposable for VfsRootAccess")
    // We might have python installation outside the project root, but we still need to have access to it.
    VfsRootAccess.allowRootAccess(disposable, pythonBinary.parent.toString())
    return Pair(pythonBinary, AutoCloseable {
      Disposer.dispose(disposable)
    })
  }
}