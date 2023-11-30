// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.model.MavenModel
import org.jetbrains.idea.maven.server.MavenServerManager
import java.nio.file.Path

class MavenProjectModelServerModelReadHelper(private val myProject: Project) : MavenProjectModelReadHelper {
  override fun interpolate(basedir: Path,
                           file: VirtualFile,
                           model: MavenModel): MavenModel {
    val pomDir = file.parent.toNioPath()
    return MavenServerManager.getInstance().getConnector(myProject, basedir.toString()).interpolateAndAlignModel(model, basedir, pomDir)
  }

  override fun assembleInheritance(projectPomDir: Path, parent: MavenModel?, model: MavenModel): MavenModel {
    return MavenServerManager.getInstance().getConnector(myProject, projectPomDir.toAbsolutePath().toString())
      .assembleInheritance(model, parent)
  }
}
