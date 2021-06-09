// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes

import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenSpyLoggedEventParser
import org.jetbrains.idea.maven.externalSystemIntegration.output.parsers.MavenEventType
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectBundle.message
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

class TargetOptionQuickFix : MavenSpyLoggedEventParser {

  override fun supportsType(type: MavenEventType) = type == MavenEventType.MOJO_FAILED

  override fun processLogLine(parentId: Any,
                              parsingContext: MavenParsingContext,
                              logLine: String,
                              messageConsumer: Consumer<in BuildEvent?>): Boolean {
    if ((logLine.contains("warning: source release") && logLine.contains("requires target release "))
        || logLine.contains("error: invalid target release")) {
      val lastErrorProject = parsingContext.startedProjects.last() + ":"
      val failedProject = parsingContext.projectsInReactor.find { it.startsWith(lastErrorProject) } ?: return false
      val mavenProject = MavenProjectsManager.getInstance(parsingContext.ideaProject).findProject(MavenId(failedProject)) ?: return false

      messageConsumer.accept(
        BuildIssueEventImpl(parentId,
                            TargetLevelBuildIssue(logLine, mavenProject),
                            MessageEvent.Kind.ERROR)
      )
      return true
    }

    return false
  }
}

class TargetLevelBuildIssue(
  override val title: String,
  mavenProject: MavenProject) : BuildIssue {

  override val quickFixes: List<UpdateTargetLevelQuickFix> = Collections.singletonList(UpdateTargetLevelQuickFix(mavenProject))
  override val description = createDescription()

  private fun createDescription() = "$title\n<br/>" + quickFixes.map { message("maven.target.level.not.supported.update", it.id) }
    .joinToString("\n<br/>")


  override fun getNavigatable(project: Project): Navigatable? {
    return null
  }
}

class UpdateTargetLevelQuickFix(val mavenProject: MavenProject) : BuildIssueQuickFix {
  override val id = ID + mavenProject.mavenId.displayString
  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val languageLevelQuickFix = LanguageLevelQuickFixFactory.getTargetInstance(project, mavenProject)
    return ProcessQuickFix.perform(languageLevelQuickFix, project, mavenProject)
  }

  companion object {
    val ID = "maven_quickfix_target_level_"
  }
}

