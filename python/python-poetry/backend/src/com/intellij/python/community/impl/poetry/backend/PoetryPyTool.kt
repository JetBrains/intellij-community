// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.poetry.backend

import com.intellij.execution.Platform
import com.intellij.python.community.impl.poetry.backend.PyPoetryBundle.message
import com.intellij.python.community.impl.poetry.common.icons.PythonCommunityImplPoetryCommonIcons
import com.intellij.python.pytools.PackageManagerPyTool
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.ToolCommandSpec
import com.intellij.python.pytools.ToolSearchPath
import com.intellij.python.pytools.pyExecutableSpec
import com.jetbrains.python.packaging.PyPackageName
import javax.swing.Icon
import org.jetbrains.annotations.ApiStatus

/**
 * [Poetry](https://python-poetry.org/) — a tool for Python dependency management and packaging. It
 * declares dependencies in `pyproject.toml`, resolves them into a `poetry.lock` file for reproducible
 * installs, manages the project's virtual environment, and builds and publishes packages.
 */
@ApiStatus.Internal
class PoetryPyTool : PyTool, PackageManagerPyTool {
  override val presentableName: String = "Poetry"
  override val packageName: PyPackageName = PyPackageName.from("poetry")
  override val description: String get() = message("python.poetry.tool.description")
  override val icon: Icon get() = PythonCommunityImplPoetryCommonIcons.Poetry
  override val toolCommandSpec: ToolCommandSpec
    get() = pyExecutableSpec(fusId, listOf(
      ToolSearchPath.RelativePathFromHome(listOf(".poetry", ".bin"), Platform.WINDOWS),
    ))

  @Suppress("CompanionObjectInExtension")
  companion object {
    fun getInstance(): PoetryPyTool = PyTool.EP_NAME.findExtensionOrFail(PoetryPyTool::class.java)
  }
}
