// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.build.events.BuildEventsNls
import com.intellij.build.events.MessageEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole
import org.jetbrains.idea.maven.buildtool.MavenSyncSession
import org.jetbrains.idea.maven.buildtool.getBuildToolWindow
import org.jetbrains.idea.maven.buildtool.quickfix.AddIntellijSdkInToolchains
import org.jetbrains.idea.maven.execution.SyncBundle
import org.jetbrains.idea.maven.externalSystemIntegration.output.importproject.MavenImportLoggedEventParser
import org.jetbrains.idea.maven.externalSystemIntegration.output.importproject.quickfixes.RepositoryBlockedSyncIssue
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenProjectProblem
import org.jetbrains.idea.maven.server.MavenServerConsoleIndicator
import org.jetbrains.idea.maven.toolchains.ToolchainFinder
import org.jetbrains.idea.maven.toolchains.ToolchainResolverSession

@ApiStatus.Internal
object MavenResolveResultProblemProcessor {
  const val BLOCKED_MIRROR_FOR_REPOSITORIES: String = "Blocked mirror for repositories:"

  fun notifySyncForProblem(project: Project, problem: MavenProjectProblem) {
    val syncConsole = MavenProjectsManager.getInstance(project).getSyncConsole()
    val message = problem.getDescription()
    if (message == null) return

    if (message.contains(BLOCKED_MIRROR_FOR_REPOSITORIES)) {
      val buildIssue = RepositoryBlockedSyncIssue.getIssue(project, problem.getDescription()!!)
      syncConsole.showBuildIssue(buildIssue)
    }
    else if (problem.getMavenArtifact() == null) {
      syncConsole
        .addWarning(SyncBundle.message("maven.sync.annotation.processor.problem"), message)
    }

    if (problem.getMavenArtifact() != null) {
      syncConsole.showArtifactBuildIssue(
        MavenServerConsoleIndicator.ResolveType.DEPENDENCY,
        problem.getMavenArtifact()!!.getMavenId().getKey(), message
      )
    }
  }

  suspend fun notifyMavenProblems(syncSession: MavenSyncSession) {
    notifyDependenciesProblems( syncSession)
    notifyToolchainsProblems(syncSession)
  }

  private suspend fun notifyDependenciesProblems(
    syncSession: MavenSyncSession,
  ) {
    val syncConsole = syncSession.getBuildToolWindow()
    for (mavenProject in syncSession.projectsTree.projects) {
      for (problem in mavenProject.problems) {
        if (!processedWithErrorParsers(problem, syncSession.project, syncConsole)) {
          syncConsole.showProblem(problem)
        }
      }
    }
  }

  private suspend fun notifyToolchainsProblems(syncSession: MavenSyncSession) {
    val finder = ToolchainFinder()
    val requirements = syncSession.projectsTree
      .projects
      .flatMap { finder.allToolchainRequirements(it) }
      .toSet()
    if (requirements.isEmpty()) return

    val toolchainSession = ToolchainResolverSession.forSession(syncSession)
    val missedRequirements = requirements.filter { toolchainSession.findOrInstallJdk(it) == null }


    missedRequirements.forEach { requirement ->
      syncSession.getBuildToolWindow().addBuildIssue(object : BuildIssue {
        override val title: @BuildEventsNls.Title String
          get() = SyncBundle.message("maven.toolchain.absent.title", requirement.description)
        override val description: @BuildEventsNls.Description String
          get() = SyncBundle.message("maven.toolchain.absent.description", requirement.description, AddIntellijSdkInToolchains.ID)
        override val quickFixes: List<BuildIssueQuickFix> = listOf(
          AddIntellijSdkInToolchains(requirement, missedRequirements.size == 1)
        )

        override fun getNavigatable(project: Project): Navigatable? = null
      }, MessageEvent.Kind.ERROR)
    }

  }


  private fun processedWithErrorParsers(problem: MavenProjectProblem, project: Project, console: MavenSyncConsole?): Boolean {
    val list = MavenImportLoggedEventParser.EP_NAME.extensionList
    for (parser in list) {
      val description = problem.getDescription()
      if (description == null) continue
      if (parser.processProjectProblem(project, problem)) {
        return true
      }
    }
    return false
  }

  class MavenResolveProblemHolder(
    val repositoryBlockedProblems: MutableSet<MavenProjectProblem>,
    val unresolvedArtifactProblems: MutableSet<MavenProjectProblem>,
    val unresolvedArtifacts: MutableSet<MavenArtifact?>,
  ) {
    val isEmpty: Boolean
      get() = repositoryBlockedProblems.isEmpty()
              && unresolvedArtifactProblems.isEmpty() && unresolvedArtifacts.isEmpty()
  }
}
