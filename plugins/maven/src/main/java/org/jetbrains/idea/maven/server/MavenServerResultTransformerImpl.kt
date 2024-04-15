// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import org.jetbrains.idea.maven.model.MavenModel

class MavenServerResultTransformerImpl : MavenServerResultTransformer {
  override fun transform(transformer: RemotePathTransformerFactory.Transformer, modelToTransformInPlace: MavenModel) {
    if (transformer !== RemotePathTransformerFactory.Transformer.ID) {
      MavenBuildPathsChange({ transformer.toIdePath(it)!! }, { transformer.canBeRemotePath(it) }).perform(modelToTransformInPlace)
    }
  }
}
