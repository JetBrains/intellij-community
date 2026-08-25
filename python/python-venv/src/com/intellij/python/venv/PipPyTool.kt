// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.venv

import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.PyToolManager
import com.intellij.python.venv.PyVenvBundle.message
import com.intellij.python.venv.icons.PythonVenvIcons
import com.jetbrains.python.packaging.PyPackageName
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * [pip](https://pip.pypa.io/) — the standard package installer for Python, maintained under the PyPA. It installs
 * and uninstalls distributions from PyPI, from version-control URLs and from local paths, resolves their
 * dependencies, and installs them in batches from a requirements file. It ships with CPython itself (via
 * `ensurepip`), so unlike the other tools it is already present in every environment rather than something the user
 * installs.
 */
@ApiStatus.Internal
class PipPyTool : PyTool {
  override val presentableName: String = "pip"

  override val packageName: PyPackageName = PyPackageName.from("pip")

  override val description: String get() = message("py.venv.pip.tool.description")

  /** The virtualenv mark: the environments whose contents pip manages here are plain venvs. */
  override val icon: Icon get() = PythonVenvIcons.VirtualEnv

  override val manager: PyToolManager? get() = null

  @Suppress("CompanionObjectInExtension")
  companion object {
    fun getInstance(): PipPyTool = PyTool.EP_NAME.findExtensionOrFail(PipPyTool::class.java)
  }
}
