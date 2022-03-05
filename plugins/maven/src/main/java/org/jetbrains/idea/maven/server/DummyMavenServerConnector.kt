// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server

import com.intellij.build.events.MessageEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.annotations.NotNull
import org.jetbrains.idea.maven.execution.SyncBundle
import org.jetbrains.idea.maven.model.*
import org.jetbrains.idea.maven.project.MavenConfigurableBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.server.security.MavenToken
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.File
import java.util.*
import java.util.concurrent.CompletableFuture


class DummyMavenServerConnector(project: @NotNull Project,
                                val manager: @NotNull MavenServerManager,
                                jdk: @NotNull Sdk,
                                vmOptions: @NotNull String,
                                mavenDistribution: @NotNull MavenDistribution,
                                multimoduleDirectory: @NotNull String) : MavenServerConnector(project, manager, jdk, vmOptions,
                                                                                              mavenDistribution, multimoduleDirectory) {
  override fun isNew() = false

  override fun isCompatibleWith(jdk: Sdk?, vmOptions: String?, distribution: MavenDistribution?) = true

  override fun connect() {
  }

  override fun getServer(): MavenServer {
    return DummyMavenServer(myProject)
  }

  override fun addDownloadListener(listener: MavenServerDownloadListener?) {
  }

  override fun removeDownloadListener(listener: MavenServerDownloadListener?) {
  }

  override fun getSupportType() = MavenConfigurableBundle.message("connector.ui.dummy")

  override fun getState() = State.RUNNING

  override fun checkConnected() = true
}

class DummyMavenServer(val project: Project) : MavenServer {


  override fun createEmbedder(settings: MavenEmbedderSettings?, token: MavenToken?): MavenServerEmbedder {
    return DummyEmbedder(project)
  }

  override fun createIndexer(token: MavenToken?): MavenServerIndexer {
    return DummyIndexer()
  }

  override fun interpolateAndAlignModel(model: MavenModel, basedir: File?, token: MavenToken?): MavenModel {
    return model
  }

  override fun assembleInheritance(model: MavenModel, parentModel: MavenModel?, token: MavenToken?): MavenModel {
    return model
  }

  override fun applyProfiles(model: MavenModel,
                             basedir: File?,
                             explicitProfiles: MavenExplicitProfiles?,
                             alwaysOnProfiles: Collection<String>?,
                             token: MavenToken?): ProfileApplicationResult {
    return ProfileApplicationResult(model, MavenExplicitProfiles.NONE)
  }

  override fun createPullLogger(token: MavenToken?): MavenPullServerLogger? {
    return null
  }

  override fun createPullDownloadListener(token: MavenToken?): MavenPullDownloadListener? {
    return null
  }
}

class TrustProjectQuickFix : BuildIssueQuickFix {
  override val id = ID

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val future = CompletableFuture<Void>()
    ApplicationManager.getApplication().invokeLater {
      try {
        val result = MavenUtil.isProjectTrustedEnoughToImport(project)
        if (result) {
          MavenProjectsManager.getInstance(project).forceUpdateAllProjectsOrFindAllAvailablePomFiles()
        }
        future.complete(null)
      }
      catch (e: Throwable) {
        future.completeExceptionally(e)
      }

    }

    return future
  }

  companion object {
    val ID = "TRUST_MAVEN_PROJECT_QUICK_FIX_ID"
  }
}

class DummyIndexer : MavenServerIndexer {

  override fun releaseIndex(id: MavenIndexId, token: MavenToken?) {
  }

  override fun getIndexCount(token: MavenToken?): Int {
    return 0
  }

  override fun updateIndex(id: MavenIndexId, settings: MavenServerSettings?, indicator: MavenServerProgressIndicator?, token: MavenToken?) {
  }

  override fun processArtifacts(indexId: MavenIndexId, startFrom: Int, token: MavenToken?): List<IndexedMavenId>? = null

  override fun addArtifact(indexId: MavenIndexId, artifactFile: File?, token: MavenToken?): IndexedMavenId {
    return IndexedMavenId(null, null, null, null, null)
  }

  override fun search(indexId: MavenIndexId, query: String, maxResult: Int, token: MavenToken?): Set<MavenArtifactInfo> {
    return emptySet()
  }

  override fun getArchetypes(token: MavenToken?): Collection<MavenArchetype> {
    return emptySet()
  }

  override fun release(token: MavenToken?) {
  }

  override fun indexExists(dir: File?, token: MavenToken?): Boolean {
    return false
  }
}

class DummyEmbedder(val myProject: Project) : MavenServerEmbedder {
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

  override fun resolveProject(files: Collection<File>,
                              activeProfiles: Collection<String>,
                              inactiveProfiles: Collection<String>,
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
