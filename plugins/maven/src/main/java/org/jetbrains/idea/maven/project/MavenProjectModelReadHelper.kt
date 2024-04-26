// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.model.MavenModel
import java.nio.file.Path

interface MavenProjectModelReadHelper {
  suspend fun interpolate(basedir: Path, file: VirtualFile, model: MavenModel): MavenModel

  suspend fun assembleInheritance(projectPomDir: Path, parent: MavenModel, model: MavenModel): MavenModel
}
