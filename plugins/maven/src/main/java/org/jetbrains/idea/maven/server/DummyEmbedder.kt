// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import com.intellij.build.events.MessageEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.execution.SyncBundle
import org.jetbrains.idea.maven.model.*
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.server.security.MavenToken
import java.io.File
import java.util.*

abstract class DummyEmbedder(val myProject: Project) : MavenServerEmbedder {
  override fun customizeAndGetProgressIndicator(workspaceMap: MavenWorkspaceMap?,
                                                failOnUnresolvedDependency: Boolean,
                                                alwaysUpdateSnapshots: Boolean,
                                                userProperties: Properties?,
                                                token: MavenToken?): MavenServerPullProgressIndicator {
    return object : MavenServerPullProgressIndicator {
      override fun pullDownloadEvents(): MutableList<MavenArtifactDownloadServerProgressEvent>? {
        return null
      }

      override fun pullConsoleEvents(): MutableList<MavenServerConsoleEvent>? {
        return null
      }

      override fun cancel() {
      }

    }
  }

  override fun customizeComponents(token: MavenToken?) {
  }

  override fun retrieveAvailableVersions(groupId: String,
                                         artifactId: String,
                                         remoteRepositories: List<MavenRemoteRepository>,
                                         token: MavenToken?): List<String> {
    return emptyList()
  }

  abstract override fun resolveProject(files: Collection<File>,
                                       activeProfiles: Collection<String>,
                                       inactiveProfiles: Collection<String>,
                                       forceResolveDependenciesSequentially: Boolean,
                                       token: MavenToken?): Collection<MavenServerExecutionResult>

  override fun evaluateEffectivePom(file: File,
                                    activeProfiles: List<String>,
                                    inactiveProfiles: List<String>,
                                    token: MavenToken?): String? {
    return null
  }

  override fun resolve(info: MavenArtifactInfo, remoteRepositories: List<MavenRemoteRepository>, token: MavenToken?): MavenArtifact {
    return MavenArtifact(info.groupId, info.artifactId, info.version, info.version, null, info.classifier, null, false, info.packaging,
                         null, null, false, true)
  }

  override fun resolveTransitively(artifacts: List<MavenArtifactInfo>,
                                   remoteRepositories: List<MavenRemoteRepository>,
                                   token: MavenToken?): List<MavenArtifact> {
    return emptyList()
  }

  override fun resolveArtifactTransitively(artifacts: MutableList<MavenArtifactInfo>,
                                           remoteRepositories: MutableList<MavenRemoteRepository>,
                                           token: MavenToken?): MavenArtifactResolveResult {
    return MavenArtifactResolveResult(emptyList(), null)
  }

  override fun resolvePlugin(plugin: MavenPlugin,
                             repositories: List<MavenRemoteRepository>,
                             nativeMavenProjectId: Int,
                             transitive: Boolean,
                             token: MavenToken?): Collection<MavenArtifact> {
    return emptyList()
  }

  override fun execute(file: File,
                       activeProfiles: Collection<String>,
                       inactiveProfiles: Collection<String>,
                       goals: List<String>,
                       selectedProjects: List<String>,
                       alsoMake: Boolean,
                       alsoMakeDependents: Boolean,
                       token: MavenToken?): MavenServerExecutionResult {
    return MavenServerExecutionResult(null, emptySet(), emptySet())
  }

  override fun reset(token: MavenToken?) {
  }

  override fun release(token: MavenToken?) {
  }

  override fun clearCaches(token: MavenToken?) {
  }

  override fun clearCachesFor(projectId: MavenId?, token: MavenToken?) {
  }

  override fun readModel(file: File?, token: MavenToken?): MavenModel? {
    return null
  }

  override fun resolveRepositories(repositories: MutableCollection<MavenRemoteRepository>,
                                   token: MavenToken?): MutableSet<MavenRemoteRepository> {
    return mutableSetOf()
  }

  override fun getArchetypes(token: MavenToken?): MutableCollection<MavenArchetype> {
    return mutableSetOf()
  }

  override fun getLocalArchetypes(token: MavenToken?, path: String): MutableCollection<MavenArchetype> {
    return mutableSetOf()
  }

  override fun getRemoteArchetypes(token: MavenToken?, url: String): MutableCollection<MavenArchetype> {
    return mutableSetOf()
  }

  override fun resolveAndGetArchetypeDescriptor(groupId: String, artifactId: String, version: String,
                                                repositories: MutableList<MavenRemoteRepository>, url: String?,
                                                token: MavenToken?): MutableMap<String, String> {
    return mutableMapOf()
  }
}

class UntrustedDummyEmbedder(myProject: Project) : DummyEmbedder(myProject) {
  override fun resolveProject(files: Collection<File>,
                              activeProfiles: Collection<String>,
                              inactiveProfiles: Collection<String>,
                              forceResolveDependenciesSequentially: Boolean,
                              token: MavenToken?): Collection<MavenServerExecutionResult> {
    MavenProjectsManager.getInstance(myProject).syncConsole.addBuildIssue(
      object : BuildIssue {
        override val title = SyncBundle.message("maven.sync.not.trusted.title")
        override val description = SyncBundle.message("maven.sync.not.trusted.description") +
                                   "\n<a href=\"${TrustProjectQuickFix.ID}\">${SyncBundle.message("maven.sync.trust.project")}</a>"
        override val quickFixes: List<BuildIssueQuickFix> = listOf(TrustProjectQuickFix())

        override fun getNavigatable(project: Project) = null

      },
      MessageEvent.Kind.WARNING
    )
    return emptyList()
  }
}

class MisconfiguredPlexusDummyEmbedder(myProject: Project,
                                       private val myExceptionMessage: String,
                                       private val myMultimoduleDirectories: MutableSet<String>,
                                       private val myMavenVersion: String?,
                                       private val myUnresolvedId: MavenId?) : DummyEmbedder(myProject) {
  override fun resolveProject(files: Collection<File>,
                              activeProfiles: Collection<String>,
                              inactiveProfiles: Collection<String>,
                              forceResolveDependenciesSequentially: Boolean,
                              token: MavenToken?): Collection<MavenServerExecutionResult> {

    MavenProjectsManager.getInstance(myProject).syncConsole.addBuildIssue(
      MavenCoreInitializationFailureIssue(myExceptionMessage,
                                          myMultimoduleDirectories,
                                          myMavenVersion,
                                          myUnresolvedId
      ),
      MessageEvent.Kind.ERROR
    )
    return emptyList()
  }

}