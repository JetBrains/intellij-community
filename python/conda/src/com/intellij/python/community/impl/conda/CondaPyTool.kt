// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.conda

import com.intellij.execution.Platform
import com.intellij.openapi.application.EDT
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelMachine
import com.intellij.platform.eel.provider.getResolvedEelMachine
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.impl.conda.icons.PythonCommunityImplCondaIcons
import com.intellij.python.community.impl.installer.CondaInstallManager
import com.intellij.python.pytools.PyExecutableCache
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.PyToolManager
import com.intellij.python.pytools.PackageManagerPyTool
import com.intellij.python.pytools.pyExecutableSpec
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.sdk.ToolCommandSpec
import com.jetbrains.python.sdk.ToolSearchPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import javax.swing.Icon

/**
 * Conda as a [PyTool] + [PackageManagerPyTool], so it appears on the Package Managers settings page and
 * reuses the per-Eel-machine custom-path store / detection cache the same way other tools do. Unlike
 * pip/uv-installable tools it is not a Python package: it installs via its own [CondaInstallManager]
 * (see [CondaPyToolManager]), and shows only on the Package Managers page (it is not an `ExternalPyTool`).
 */
class CondaPyTool : PyTool, PackageManagerPyTool {
  override val presentableName: String = "Conda"
  override val packageName: PyPackageName = PyPackageName.from("conda")
  override val description: String get() = PyCondaBundle.message("python.conda.tool.description")
  override val icon: Icon get() = PythonCommunityImplCondaIcons.Anaconda
  override val manager: PyToolManager = CondaPyToolManager

  override val toolCommandSpec: ToolCommandSpec = pyExecutableSpec(fusId, listOf(
    ToolSearchPath.RelativePathFromHome(listOf("anaconda3", "bin"), Platform.UNIX),
    ToolSearchPath.RelativePathFromHome(listOf("miniconda3", "bin"), Platform.UNIX),
    ToolSearchPath.AbsolutePath("/usr/local/bin", Platform.UNIX),
    ToolSearchPath.RelativePathFromHome(listOf("opt", "miniconda3", "bin"), Platform.UNIX),
    ToolSearchPath.RelativePathFromHome(listOf("opt", "anaconda3", "bin"), Platform.UNIX),
    ToolSearchPath.AbsolutePath("/opt/miniconda3/condabin", Platform.UNIX),
    ToolSearchPath.AbsolutePath("/opt/conda/bin", Platform.UNIX),
    ToolSearchPath.AbsolutePath("/opt/anaconda3/condabin", Platform.UNIX),
    ToolSearchPath.AbsolutePath("/opt/homebrew/anaconda3/bin", Platform.UNIX),
    ToolSearchPath.RelativePath("ALLUSERSPROFILE", listOf("Anaconda3", "condabin"), Platform.WINDOWS),
    ToolSearchPath.RelativePath("ALLUSERSPROFILE", listOf("Miniconda3", "condabin"), Platform.WINDOWS),
    ToolSearchPath.RelativePath("USERPROFILE", listOf("Anaconda3", "condabin"), Platform.WINDOWS),
    ToolSearchPath.RelativePath("USERPROFILE", listOf("Miniconda3", "condabin"), Platform.WINDOWS),
  ))

  @Suppress("CompanionObjectInExtension")
  companion object {
    fun getInstance(): CondaPyTool = PyTool.EP_NAME.findExtensionOrFail(CondaPyTool::class.java)
  }
}

/**
 * Conda's install strategy. Conda is not a Python package, so install reuses the Miniconda installer
 * ([CondaInstallManager]) — which is local-only and runs its own modal on the EDT — then resolves the
 * freshly installed executable through [PyExecutableCache]. Updating conda from the IDE is not supported.
 */
private object CondaPyToolManager : PyToolManager {
  override suspend fun install(tool: PyTool, eel: EelApi): PyResult<Path> {
    withContext(Dispatchers.EDT) {
      CondaInstallManager.installLatest(project = null)
    }
    val cache = PyExecutableCache.getInstance()
    cache.invalidate(localEel.descriptor, tool)
    return cache.get(localEel.descriptor, tool)?.let { PyResult.success(it) }
           ?: PyResult.localizedError(PyCondaBundle.message("python.conda.install.not.detected"))
  }

  override suspend fun upgrade(tool: PyTool, eel: EelApi): PyResult<Path> =
    PyResult.localizedError(PyCondaBundle.message("python.conda.update.not.supported"))

  /** The Miniconda installer only targets the local machine, so Install is offered on local eels only. */
  override fun canInstall(eelDescriptor: EelDescriptor): Boolean =
    eelDescriptor.getResolvedEelMachine() is LocalEelMachine
}
