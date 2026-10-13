// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.poetry.backend.sdk

import java.nio.file.Path
import com.jetbrains.python.sdk.PySdkDataProvider
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.intellij.python.sdk.common.PyEnvRef
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.project.PyProject
import com.intellij.python.pytools.common.FusId
import com.intellij.python.community.impl.poetry.backend.PoetryPyTool
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.jetbrains.python.sdk.PySdkProvider
import com.jetbrains.python.sdk.poetry.PyPoetrySdkAdditionalData
import org.jdom.Element

/**
 *  This source code is created by @koxudaxi Koudai Aono <koxudaxi@gmail.com>
 */

internal class PoetrySdkProvider : PySdkProvider {
  override fun loadAdditionalDataForSdk(element: Element): SdkAdditionalData? {
    return PyPoetrySdkAdditionalData.load(element)
  }
}

/** Adopts a poetry env of the project, in-project `.venv` or a cache env, as a poetry-typed SDK. */
internal class PoetrySdkDataProvider : PySdkDataProvider {
  override val manager: FusId get() = PoetryPyTool.getInstance().fusId

  override suspend fun <P : PathHolder> sdkDataOf(
    pyProject: PyProject,
    envRef: PyEnvRef,
    pythonBinary: P,
    fileSystem: FileSystem<P>,
    requirementsFile: Path?,
  ): PythonSdkAdditionalData =
    PyPoetrySdkAdditionalData(pyProject.baseDir).also { it.requirementsPath = requirementsFile }
}
