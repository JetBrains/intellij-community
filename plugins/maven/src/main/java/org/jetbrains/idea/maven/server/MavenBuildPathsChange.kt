// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server

import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenArtifactNode
import org.jetbrains.idea.maven.model.MavenBuild
import org.jetbrains.idea.maven.model.MavenModel
import org.jetbrains.idea.maven.model.MavenSource
import java.io.File

class MavenBuildPathsChange(
  private val transformer: (String) -> String,
) {

  fun perform(model: MavenModel): Unit = model.transformPaths()

  private fun MavenModel.transformPaths() {
    dependencies = dependencies.map { it.transformPaths() }
    dependencyTree = dependencyTree.map { it.transformPaths(null) }
    build.transformPaths()
  }

  private fun MavenBuild.transformPaths() {
    directory = transformer(directory)
    outputDirectory = transformer(outputDirectory)
    testOutputDirectory = transformer(testOutputDirectory)
    mavenSources = mavenSources.map { it.transformPaths() }
  }

  private fun MavenSource.transformPaths() = this.withNewDirectory(
    transformer(directory)
  )

  private fun MavenArtifact.transformPaths(): MavenArtifact {
    return this.replaceFile(File(transformer(file.path)), null)
  }

  private fun MavenArtifactNode.transformPaths(parent: MavenArtifactNode?): MavenArtifactNode {
    val result = MavenArtifactNode(
      parent,
      artifact.transformPaths(),
      state,
      relatedArtifact?.transformPaths(),
      originalScope,
      premanagedVersion,
      premanagedScope
    )
    result.dependencies = dependencies.map { it.transformPaths(result) }
    return result
  }
}





