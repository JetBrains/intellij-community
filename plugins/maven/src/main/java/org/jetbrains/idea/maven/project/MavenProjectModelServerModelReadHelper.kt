// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.model.MavenModel
import org.jetbrains.idea.maven.server.MavenServerConnector
import org.jetbrains.idea.maven.server.MavenServerManager
import java.nio.file.Path

open class MavenProjectModelServerModelReadHelper(protected val myProject: Project) : MavenProjectModelReadHelper {
  override suspend fun interpolate(basedir: Path,
                                   mavenModuleFile: VirtualFile,
                                   model: MavenModel): MavenModel {
    val pomDir = mavenModuleFile.parent.toNioPath()
    return MavenServerConnector.interpolateAndAlignModel(myProject,model, basedir, pomDir )
  }

  override suspend fun assembleInheritance(projectPomDir: Path, parent: MavenModel, model: MavenModel, mavenModuleFile: VirtualFile): MavenModel {
    return MavenServerConnector.assembleInheritance(myProject, projectPomDir, model, parent)
  }

  override fun filterModules(modules: List<String>, mavenModuleFile: VirtualFile): List<String> {
    return modules;
  }
}
