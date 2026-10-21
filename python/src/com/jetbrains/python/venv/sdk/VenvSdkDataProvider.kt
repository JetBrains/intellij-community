// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.venv.sdk

import java.nio.file.Path
import com.jetbrains.python.sdk.PySdkDataProvider
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.intellij.python.sdk.common.PyEnvRef
import com.intellij.python.pytools.common.FusId
import com.intellij.python.venv.PipPyTool
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.add.v2.PathHolder

/**
 * The SDK data of a plain virtualenv or a system Python, which the `pip` manager names. Such an interpreter has no
 * tool-specific SDK, so its flavor is guessed from the path.
 */
internal class VenvSdkDataProvider : PySdkDataProvider {
  override val manager: FusId get() = PipPyTool.getInstance().fusId

  override suspend fun <P : PathHolder> sdkDataOf(
    pyProject: PyProject,
    envRef: PyEnvRef,
    pythonBinary: P,
    fileSystem: FileSystem<P>,
    requirementsFile: Path?,
  ): PythonSdkAdditionalData =
    PythonSdkAdditionalData(fileSystem.flavorAndDataOf(pythonBinary), pyProject.baseDir).also { it.requirementsPath = requirementsFile }
}
