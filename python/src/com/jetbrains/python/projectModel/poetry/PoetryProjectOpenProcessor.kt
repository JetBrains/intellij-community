// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.poetry

import com.jetbrains.python.PyBundle
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.projectModel.PyProjectTomlOpenProcessorBase
import org.jetbrains.annotations.Nls
import javax.swing.Icon


internal class PoetryProjectOpenProcessor : PyProjectTomlOpenProcessorBase() {
  override val importProvider = PoetryProjectOpenProvider()
  override val name: @Nls String = PyBundle.message("python.project.model.poetry")
  override val icon: Icon = PythonIcons.Python.Origami
}