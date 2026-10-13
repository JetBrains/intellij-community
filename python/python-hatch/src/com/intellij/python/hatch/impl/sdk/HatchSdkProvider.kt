// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.hatch.impl.sdk

import java.nio.file.Path
import com.jetbrains.python.sdk.PySdkDataProvider
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.intellij.python.sdk.common.PyEnvRef
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.python.hatch.HatchPyTool
import com.intellij.python.pytools.common.FusId
import com.jetbrains.python.hatch.sdk.HatchSdkAdditionalData
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.sdk.PySdkProvider
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.add.v2.PathHolder
import org.jdom.Element

internal class HatchSdkProvider : PySdkProvider {
  override fun loadAdditionalDataForSdk(element: Element): SdkAdditionalData? = HatchSdkAdditionalData.createIfHatch(element)
}

/** Adopts the existing hatch env at the env ref, which is its environment name, as a hatch-typed SDK. */
internal class HatchSdkDataProvider : PySdkDataProvider {
  override val manager: FusId get() = HatchPyTool.getInstance().fusId

  override suspend fun <P : PathHolder> sdkDataOf(
    pyProject: PyProject,
    envRef: PyEnvRef,
    pythonBinary: P,
    fileSystem: FileSystem<P>,
    requirementsFile: Path?,
  ): PythonSdkAdditionalData =
    HatchSdkAdditionalData(hatchWorkingDirectory = pyProject.baseDir, hatchEnvironmentName = envRef.value)
      .also { it.requirementsPath = requirementsFile }
}
