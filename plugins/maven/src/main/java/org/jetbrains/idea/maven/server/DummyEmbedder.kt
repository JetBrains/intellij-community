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

private val emptyByteArray = ByteArray(0)

abstract class DummyEmbedder : MavenServerEmbedder {
  override fun evaluateEffectivePom(file: File,
                                    activeProfiles: ArrayList<String>,
                                    inactiveProfiles: ArrayList<String>,
                                    token: MavenToken?): String? {
    return null
  }

  override fun resolveArtifacts(longRunningTaskInput: LongRunningTaskInput,
                                requests: ArrayList<MavenArtifactResolutionRequest>,
                                token: MavenToken?): MavenServerResponse<ArrayList<MavenArtifact>> {
    return MavenServerResponse(ArrayList(), LongRunningTaskStatus.EMPTY)
  }

  override fun resolveArtifactsTransitively(artifacts: ArrayList<MavenArtifactInfo>,
                                            remoteRepositories: ArrayList<MavenRemoteRepository>,
                                            token: MavenToken?): MavenArtifactResolveResult {
    return MavenArtifactResolveResult(emptyList(), null)
  }

  override fun resolvePlugins(longRunningTaskInput: LongRunningTaskInput,
                              pluginResolutionRequests: ArrayList<PluginResolutionRequest>,
                              forceUpdateSnapshots: Boolean,
                              token: MavenToken?): MavenServerResponse<ArrayList<PluginResolutionResponse>> {
    return MavenServerResponse(ArrayList(), LongRunningTaskStatus.EMPTY)
  }

  override fun executeGoal(longRunningTaskInput: LongRunningTaskInput,
                           requests: ArrayList<MavenGoalExecutionRequest>,
                           goal: String,
                           token: MavenToken?): MavenServerResponse<ArrayList<MavenGoalExecutionResult>> {
    return MavenServerResponse(ArrayList(), LongRunningTaskStatus.EMPTY)
  }

  override fun release(token: MavenToken?) {
  }

  override fun readModel(file: File?, token: MavenToken?): MavenModel? {
    return null
  }

  override fun resolveRepositories(repositories: ArrayList<MavenRemoteRepository>,
                                   token: MavenToken?): HashSet<MavenRemoteRepository> {
    return HashSet()
  }

  override fun getLocalArchetypes(token: MavenToken?, path: String): ArrayList<MavenArchetype> {
    return ArrayList()
  }

  override fun getRemoteArchetypes(token: MavenToken?, url: String): ArrayList<MavenArchetype> {
    return ArrayList()
  }

  override fun resolveAndGetArchetypeDescriptor(groupId: String, artifactId: String, version: String,
                                                repositories: ArrayList<MavenRemoteRepository>, url: String?,
                                                token: MavenToken?): HashMap<String, String> {
    return HashMap()
  }

  override fun getLongRunningTaskStatus(longRunningTaskId: String, token: MavenToken?): LongRunningTaskStatus = LongRunningTaskStatus.EMPTY

  override fun cancelLongRunningTask(longRunningTaskId: String, token: MavenToken?) = true

  override fun ping(token: MavenToken?) = true
}

class UntrustedDummyEmbedder(val myProject: Project) : DummyEmbedder() {
  override fun resolveProjects(longRunningTaskInput: LongRunningTaskInput,
                               request: ProjectResolutionRequest,
                               token: MavenToken?): MavenServerResponse<ArrayList<MavenServerExecutionResult>> {
    showUntrustedProjectNotification(myProject)
    return MavenServerResponse(ArrayList(), LongRunningTaskStatus.EMPTY)
  }


}

class MisconfiguredPlexusDummyEmbedder(private val myProject: Project,
                                       private val myExceptionMessage: String,
                                       private val myMultimoduleDirectories: MutableSet<String>,
                                       private val myMavenVersion: String?,
                                       private val myUnresolvedId: MavenId?) : DummyEmbedder() {
  override fun resolveProjects(longRunningTaskInput: LongRunningTaskInput,
                               request: ProjectResolutionRequest,
                               token: MavenToken?): MavenServerResponse<ArrayList<MavenServerExecutionResult>> {

    MavenProjectsManager.getInstance(myProject).syncConsole.addBuildIssue(
      MavenCoreInitializationFailureIssue(myExceptionMessage,
                                          myMultimoduleDirectories,
                                          myMavenVersion,
                                          myUnresolvedId
      ),
      MessageEvent.Kind.ERROR
    )
    return MavenServerResponse(ArrayList(), LongRunningTaskStatus.EMPTY)
  }

}

fun showUntrustedProjectNotification(project: Project) {
  MavenProjectsManager.getInstance(project).syncConsole.addBuildIssue(
    object : BuildIssue {
      override val title = SyncBundle.message("maven.sync.not.trusted.title")
      override val description = SyncBundle.message("maven.sync.not.trusted.description") +
                                 "\n<a href=\"${TrustProjectQuickFix.ID}\">${SyncBundle.message("maven.sync.trust.project")}</a>"
      override val quickFixes: List<BuildIssueQuickFix> = listOf(TrustProjectQuickFix())

      override fun getNavigatable(project: Project) = null

    },
    MessageEvent.Kind.WARNING
  )
}