// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.model.MavenModel
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper
import org.jetbrains.idea.maven.server.MavenRemoteObjectWrapper

open class MavenProjectModelServerModelReadHelper(protected val myProject: Project) : MavenProjectModelReadHelper {
  override suspend fun assembleInheritance(embedder: MavenEmbedderWrapper, parent: MavenModel, model: MavenModel, mavenModuleFile: VirtualFile): MavenModel {
    return embedder.assembleInheritance(model, parent, MavenRemoteObjectWrapper.ourToken)
  }

  override fun filterModules(modules: List<String>, mavenModuleFile: VirtualFile): List<String> {
    return modules;
  }
}
