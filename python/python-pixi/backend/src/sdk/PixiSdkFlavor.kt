// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.sdk

import com.intellij.icons.AllIcons
import com.intellij.python.pixi.backend.PixiPyTool
import com.intellij.python.pixi.backend.workspace.findPixiWorkspace
import com.intellij.python.pytools.common.FusId
import com.intellij.python.sdk.common.PyEnvRef
import com.jetbrains.python.PyInternalExecApi
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.sdk.PySdkDataProvider
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.PythonFlavorProvider
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.exists
import javax.swing.Icon

@PyInternalExecApi
internal object PixiSdkFlavor : CPythonSdkFlavor<PyFlavorData.Empty>() {
  override fun getIcon(): Icon = AllIcons.Nodes.Package
  override fun getFlavorDataClass(): Class<PyFlavorData.Empty> = PyFlavorData.Empty::class.java
  override fun getManagerId(): String = PixiPyTool.getInstance().fusId.value
  override fun toolEnvRefOf(pythonBinary: PathHolder, data: PyFlavorData.Empty): String? = when (pythonBinary) {
    is PathHolder.Eel -> generateSequence(pythonBinary.path.parent) { it.parent }.firstOrNull { environmentRoot ->
      environmentRoot.parent?.fileName?.toString() == "envs" && environmentRoot.parent?.parent?.fileName?.toString() == ".pixi"
    }?.fileName?.toString()
    is PathHolder.Target -> {
      val parts = pythonBinary.pathString.split('/', '\\')
      parts.indices.lastOrNull { index -> index > 0 && index + 2 < parts.size && parts[index - 1] == ".pixi" && parts[index] == "envs" }
        ?.let { parts[it + 1] }
    }
  }

  override fun isValidSdkPath(pythonBinaryPath: Path): Boolean = false
  override fun isPlatformIndependent(): Boolean = true
}

internal class PixiSdkFlavorProvider : PythonFlavorProvider {
  override fun getFlavor(): PythonSdkFlavor<*> = PixiSdkFlavor
}

internal class PixiSdkDataProvider : PySdkDataProvider {
  override val manager: FusId get() = PixiPyTool.getInstance().fusId

  override suspend fun <P : PathHolder> sdkDataOf(
    pyProject: PyProject,
    envRef: PyEnvRef,
    pythonBinary: P,
    fileSystem: FileSystem<P>,
    requirementsFile: Path?,
  ): PythonSdkAdditionalData = createPixiSdkData(pyProject.baseDir, requirementsFile)
}

internal suspend fun createPixiSdkData(workingDirectory: Path, requirementsFile: Path? = null): PythonSdkAdditionalData =
  withContext(Dispatchers.IO) {
    val workspace = findPixiWorkspace(workingDirectory)
    val manifest = workspace?.root?.let { root ->
      root.resolve("pixi.toml").takeIf { it.exists() } ?: root.resolve("pyproject.toml")
    } ?: requirementsFile
    PythonSdkAdditionalData(PyFlavorAndData(PyFlavorData.Empty, PixiSdkFlavor), workingDirectory).also {
      it.requirementsPath = manifest
    }
  }
