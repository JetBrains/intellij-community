// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server

import com.intellij.util.containers.ContainerUtil
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenBuild
import org.jetbrains.idea.maven.model.MavenModel
import org.jetbrains.idea.maven.model.MavenResource
import java.io.File

class MavenBuildPathsChange(private val transformer: (String) -> String) {
  fun perform(model: MavenModel) = model.transformPaths()

  private fun MavenModel.transformPaths() {
    dependencies = dependencies.map { it.transformPaths() }
    build.transformPaths()
  }

  private fun MavenBuild.transformPaths() {
    sources = ContainerUtil.map(sources) { transformer(it) }
    testSources = ContainerUtil.map(testSources) { transformer(it) }
    directory = transformer(directory)
    outputDirectory = transformer(outputDirectory)
    testOutputDirectory = transformer(testOutputDirectory)
    resources = resources.map { it.transformPaths() }
    testResources = testResources.map { it.transformPaths() }
  }

  private fun MavenResource.transformPaths() = MavenResource(
    transformer(directory),
    isFiltered,
    targetPath /*todo*/,
    includes,
    excludes
  )

  private fun MavenArtifact.transformPaths() = this.replaceFile(
    File(transformer(file.path)),
    null
  )
}