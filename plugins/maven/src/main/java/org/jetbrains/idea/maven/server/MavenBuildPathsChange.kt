// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server

import com.intellij.util.containers.ContainerUtil
import org.jdom.Element
import org.jetbrains.idea.maven.model.*
import java.io.File


class MavenBuildPathsChange(
  private val transformer: (String) -> String,
  private val needTransformConfiguration: (String) -> Boolean = { false },
) {
  fun perform(model: MavenModel) = model.transformPaths()

  private fun MavenModel.transformPaths() {
    dependencies = dependencies.map { it.transformPaths() }
    dependencyTree = dependencyTree.map { it.transformPaths(null) }
    build.transformPaths()
    this.plugins
      .map { it.configurationElement }
      .forEach { it.transformConfiguration() }
    this.plugins
      .flatMap { it.executions }
      .map { it.configurationElement }
      .forEach { it.transformConfiguration() }
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

  private fun Element?.transformConfiguration() {
    if (this == null) return
    if (needTransformConfiguration(textTrim)) {
      text = transformer(text)
    }
    this.children.forEach { it.transformConfiguration() }
  }
}





