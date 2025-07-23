// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.util.Comparing
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.model.*
import java.io.File
import java.io.Serializable
import java.util.*

@ApiStatus.Experimental
data class MavenPluginWithArtifact(val plugin: MavenPlugin, val artifact: MavenArtifact?) : Serializable

@ApiStatus.Experimental
data class GroupAndArtifact(val groupId: String, val artifactId: String) : Serializable

@ApiStatus.Internal
data class MavenProjectState(
  val lastReadStamp: Long = 0,
  val mavenId: MavenId? = null,
  val parentId: MavenId? = null,
  val packaging: String? = null,
  val name: String? = null,
  val finalName: String? = null,
  val defaultGoal: String? = null,
  val buildDirectory: String? = null,
  val outputDirectory: String? = null,
  val testOutputDirectory: String? = null,
  val sources: List<String> = emptyList(),
  val testSources: List<String> = emptyList(),
  val resources: List<MavenResource> = emptyList(),
  val testResources: List<MavenResource> = emptyList(),
  val filters: List<String> = emptyList(),
  val properties: Properties? = null,
  val extensions: List<MavenArtifact> = emptyList(),
  val dependencies: List<MavenArtifact> = emptyList(),
  val dependencyTree: List<MavenArtifactNode> = emptyList(),
  val remoteRepositories: List<MavenRemoteRepository> = emptyList(),
  val remotePluginRepositories: List<MavenRemoteRepository> = emptyList(),
  val annotationProcessors: List<MavenArtifact> = emptyList(),
  val managedDependencies: Map<GroupAndArtifact, String> = emptyMap(),
  val modulesPathsAndNames: Map<String, String> = emptyMap(),
  val modelMap: Map<String, String> = emptyMap(),
  val profilesIds: Collection<String> = emptySet(),
  val activatedProfilesIds: MavenExplicitProfiles = MavenExplicitProfiles.NONE,
  val dependencyHash: String? = null,
  val unresolvedArtifactIds: Set<MavenId> = emptySet(),
  // do not use nio.Path here, it's not serializable
  val localRepository: File? = null,
  val pluginInfos: List<MavenPluginWithArtifact> = emptyList(),
  val readingProblems: Collection<MavenProjectProblem> = emptySet(),
) : Serializable {
  val plugins: List<MavenPlugin> get() = pluginInfos.map { it.plugin }
  val declaredPluginInfos: List<MavenPluginWithArtifact> get() = pluginInfos.filter { !it.plugin.isDefault }
  val declaredPlugins: List<MavenPlugin> get() = declaredPluginInfos.map { it.plugin }

  val isParentResolved: Boolean
    get() = !unresolvedArtifactIds.contains(parentId)

  fun getChanges(newState: MavenProjectState): MavenProjectChanges {
    if (lastReadStamp == 0L) return MavenProjectChanges.ALL

    val result = MavenProjectChangesBuilder()

    result.setHasPackagingChanges(packaging != newState.packaging)

    result.setHasOutputChanges(finalName != newState.finalName
                               || buildDirectory != newState.buildDirectory
                               || outputDirectory != newState.outputDirectory
                               || testOutputDirectory != newState.testOutputDirectory)

    result.setHasSourceChanges(!Comparing.equal(sources, newState.sources)
                               || !Comparing.equal(testSources, newState.testSources)
                               || !Comparing.equal(resources, newState.resources)
                               || !Comparing.equal(testResources, newState.testResources))

    val repositoryChanged = !Comparing.equal(localRepository, newState.localRepository)

    result.setHasDependencyChanges(repositoryChanged || !Comparing.equal(dependencies, newState.dependencies))
    result.setHasPluginChanges(repositoryChanged || !Comparing.equal(pluginInfos, newState.pluginInfos))
    result.setHasPropertyChanges(!Comparing.equal(properties, newState.properties))
    return result
  }
}