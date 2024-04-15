// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import java.util.ArrayList

class MavenServerResultTransformerImpl : MavenServerResultTransformer {
  override fun transform(transformer: RemotePathTransformerFactory.Transformer, results: ArrayList<MavenServerExecutionResult>): Collection<MavenServerExecutionResult> {
    if (transformer !== RemotePathTransformerFactory.Transformer.ID) {
      for (result in results) {
        val data = result.projectData ?: continue
        MavenBuildPathsChange({ transformer.toIdePath(it)!! }, { transformer.canBeRemotePath(it) }).perform(data.mavenModel)
      }
    }
    return results
  }
}
