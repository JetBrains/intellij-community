// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.uv.backend

import com.intellij.python.pytools.PyTool
import com.intellij.python.uv.backend.PyUvBundle.message
import com.intellij.python.uv.common.icons.PythonUvCommonIcons
import com.jetbrains.python.packaging.PyPackageName
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * [uv](https://docs.astral.sh/uv/) — an extremely fast Python package and project manager written in
 * Rust by Astral. It resolves and installs dependencies, creates and manages virtual environments,
 * installs and switches Python versions, builds and publishes packages, and installs standalone
 * command-line tools into isolated environments (`uv tool install`). It aims to replace pip, pipx,
 * pip-tools, virtualenv, and pyenv with a single fast tool.
 */
@ApiStatus.Internal
class UvPyTool : PyTool {
  override val presentableName: String = "uv"
  override val packageName: PyPackageName = PyPackageName.from("uv")
  override val description: String get() = message("python.uv.tool.description")
  override val icon: Icon get() = PythonUvCommonIcons.UV

  @Suppress("CompanionObjectInExtension")
  companion object {
    fun getInstance(): UvPyTool = PyTool.EP_NAME.findExtensionOrFail(UvPyTool::class.java)
  }
}
