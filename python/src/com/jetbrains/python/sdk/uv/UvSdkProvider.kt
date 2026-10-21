// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import java.nio.file.Path
import com.jetbrains.python.sdk.PySdkDataProvider
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.intellij.python.sdk.common.PyEnvRef
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.python.pytools.common.FusId
import com.intellij.python.uv.backend.UvPyTool
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.sdk.PySdkProvider
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.add.v2.PathHolder
import org.jdom.Element

internal class UvSdkProvider : PySdkProvider {
  override fun loadAdditionalDataForSdk(element: Element): SdkAdditionalData? {
    return UvSdkAdditionalData.load(element)
  }
}

/**
 * Adopts an existing virtualenv as a uv env. The mode follows the project: a `pyproject.toml` makes it a project,
 * anything else a pip-mode environment. The uv path is not stored: uv is resolved when it runs, as for the other tools.
 */
internal class UvSdkDataProvider : PySdkDataProvider {
  override val manager: FusId get() = UvPyTool.getInstance().fusId

  override suspend fun <P : PathHolder> sdkDataOf(
    pyProject: PyProject,
    envRef: PyEnvRef,
    pythonBinary: P,
    fileSystem: FileSystem<P>,
    requirementsFile: Path?,
  ): PythonSdkAdditionalData {
    val baseDir = pyProject.baseDir
    return uvSdkDataOf(pythonBinary, baseDir).also { it.requirementsPath = requirementsFile ?: detectUvMode(baseDir).requirementsFile }
  }
}
