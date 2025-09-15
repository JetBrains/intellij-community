// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.uv

import com.jetbrains.python.PyBundle
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.projectModel.PyProjectTomlOpenProcessorBase
import org.jetbrains.annotations.Nls
import javax.swing.Icon

internal class UvProjectOpenProcessor : PyProjectTomlOpenProcessorBase() {
  override val importProvider = UvProjectOpenProvider()
  override val name: @Nls String = PyBundle.message("python.project.model.uv")
  override val icon: Icon = PythonIcons.UV
}