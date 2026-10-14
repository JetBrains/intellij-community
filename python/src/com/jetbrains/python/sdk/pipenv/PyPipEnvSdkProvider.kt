// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.pipenv

import java.nio.file.Path
import com.jetbrains.python.sdk.PySdkDataProvider
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.intellij.python.sdk.common.PyEnvRef
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.python.community.impl.pipenv.PipEnvPyTool
import com.intellij.python.pytools.common.FusId
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.sdk.PySdkProvider
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.add.v2.PathHolder
import org.jdom.Element

internal class PyPipEnvSdkProvider : PySdkProvider {
  override fun loadAdditionalDataForSdk(element: Element): SdkAdditionalData? {
    return PyPipEnvSdkAdditionalData.load(element)
  }
}

/** Adopts the project's existing pipenv environment as a pipenv-typed SDK. */
internal class PyPipEnvSdkDataProvider : PySdkDataProvider {
  override val manager: FusId get() = PipEnvPyTool.getInstance().fusId

  override suspend fun <P : PathHolder> sdkDataOf(
    pyProject: PyProject,
    envRef: PyEnvRef,
    pythonBinary: P,
    fileSystem: FileSystem<P>,
    requirementsFile: Path?,
  ): PythonSdkAdditionalData =
    PyPipEnvSdkAdditionalData(pyProject.baseDir).also { it.requirementsPath = requirementsFile }
}
