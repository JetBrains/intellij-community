// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.model.MavenModel
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper

interface MavenProjectModelReadHelper {
  suspend fun interpolate(embedder: MavenEmbedderWrapper, mavenModuleFile: VirtualFile, model: MavenModel): MavenModel

  suspend fun assembleInheritance(embedder: MavenEmbedderWrapper, parent: MavenModel, model: MavenModel, mavenModuleFile: VirtualFile): MavenModel

  fun filterModules(modules: List<String>, mavenModuleFile: VirtualFile): List<String>

  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<MavenProjectModelReadHelper>()
  }
}
