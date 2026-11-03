// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.pixi.common.icons.PythonPixiCommonIcons
import com.jetbrains.python.requirements.PyDependenciesFile
import com.jetbrains.python.requirements.PyDependenciesFileProvider
import javax.swing.Icon

internal class PixiDependenciesFileProvider : PyDependenciesFileProvider {
  override suspend fun fromFile(file: VirtualFile): PyDependenciesFile? =
    file.takeIf { it.name == "pixi.toml" }?.let { PixiDependenciesFile(it) }
}

private data class PixiDependenciesFile(override val virtualFile: VirtualFile) : PyDependenciesFile {
  override val icon: Icon = PythonPixiCommonIcons.Pixi
}
