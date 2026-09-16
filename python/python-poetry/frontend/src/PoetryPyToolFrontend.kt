// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.poetry.frontend

import com.intellij.python.community.impl.poetry.common.icons.PythonCommunityImplPoetryCommonIcons
import com.intellij.python.pytools.frontend.PackageManagerPyToolFrontend
import com.intellij.python.pytools.common.PyToolId
import com.intellij.python.community.impl.poetry.frontend.PyPoetryFrontendBundle.message
import javax.swing.Icon

internal class PoetryPyToolFrontend : PackageManagerPyToolFrontend {
  override val presentableName: String = "Poetry"
  override val toolId: PyToolId = PyToolId("poetry")
  override val description: String get() = message("python.poetry.tool.description")
  override val icon: Icon get() = PythonCommunityImplPoetryCommonIcons.Poetry
}
