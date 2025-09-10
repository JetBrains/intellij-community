// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.model.MavenModel

interface MavenServerResultTransformer {

  fun transform(modelToTransformInPlace: MavenModel, moduleFile: VirtualFile)

  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<MavenServerResultTransformer>()

    @JvmStatic
    fun transformPaths(transformer: RemotePathTransformerFactory.Transformer, modelToTransformInPlace: MavenModel) {
      if (transformer !== RemotePathTransformerFactory.Transformer.ID) {
        MavenBuildPathsChange { transformer.toIdePath(it)!! }.perform(modelToTransformInPlace)
      }
    }

  }
}
