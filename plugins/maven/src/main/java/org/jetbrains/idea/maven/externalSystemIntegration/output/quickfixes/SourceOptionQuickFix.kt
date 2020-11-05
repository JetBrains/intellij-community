// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes

import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.pom.Navigatable
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.concurrency.asCompletableFuture
import org.jetbrains.idea.maven.externalSystemIntegration.output.LogMessageType
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLogEntryReader
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLogEntryReader.MavenLogEntry
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLoggedEventParser
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext
import org.jetbrains.idea.maven.importing.MavenModuleImporter
import org.jetbrains.idea.maven.importing.MavenProjectModelModifier
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

class SourceOptionQuickFix : MavenLoggedEventParser {
  override fun supportsType(type: LogMessageType?): Boolean {
    return type == LogMessageType.ERROR
  }

  override fun checkLogLine(parentId: Any,
                            parsingContext: MavenParsingContext,
                            logLine: MavenLogEntry,
                            logEntryReader: MavenLogEntryReader,
                            messageConsumer: Consumer<in BuildEvent?>): Boolean {
    if (logLine.line.startsWith("Source option 5 is no longer supported. Use 6 or later")) {
      val targetLine = logEntryReader.readLine()

      if (targetLine != null && !targetLine.line.startsWith("Target option 1.5 is no longer supported. Use 1.6 or later.")) {
        logEntryReader.pushBack()
      }
      val failedProject = parsingContext.projectsInReactor.last()
      messageConsumer.accept(BuildIssueEventImpl(parentId, Source5BuildIssue(parsingContext.ideaProject, failedProject), MessageEvent.Kind.ERROR));
      return true
    }

    return false
  }

}

class Source5BuildIssue(private val project: Project, private val failedProjectId: String) : BuildIssue {

  override val quickFixes: List<UpdateSourceLevelQuickFix> = prepareQuickFixes(project, failedProjectId)
  override val title = MavenProjectBundle.message("maven.source.5.not.supported.title")
  override val description = createDescription()

  private fun createDescription() = quickFixes.map {
    HtmlChunk.link(it.id, MavenProjectBundle.message("maven.source.5.not.supported.update", it.mavenProject.displayName))
      .toString()
  }.joinToString("\n")



  override fun getNavigatable(project: Project): Navigatable? {
    val mavenProject = MavenProjectsManager.getInstance(project).findProject(MavenId(failedProjectId))
    return mavenProject?.file?.let{ OpenFileDescriptor(project, it) }
  }

  companion object {
    private fun prepareQuickFixes(project: Project, failedProjectId: String): List<UpdateSourceLevelQuickFix> {
      var mavenProject = MavenProjectsManager.getInstance(project).findProject(MavenId(failedProjectId));
      val result = ArrayList<UpdateSourceLevelQuickFix>()
      while (mavenProject != null) {
        result.add(UpdateSourceLevelQuickFix(mavenProject))
        val parentId = mavenProject.parentId
        mavenProject = parentId?.let { MavenProjectsManager.getInstance(project).findProject(parentId) }
      }
      return result
    }
  }
}

class UpdateSourceLevelQuickFix(val mavenProject: MavenProject) : BuildIssueQuickFix {
  override val id = ID + mavenProject.mavenId.displayString
  override fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*> {

    val languageLevel = MavenModuleImporter.getLanguageLevel(mavenProject)
    if(languageLevel.isAtLeast(LanguageLevel.JDK_1_6)){
      Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP, "",
                   MavenProjectBundle.message("maven.quickfix.cannot.update.source.level.already.1.6", mavenProject.displayName),
                   NotificationType.INFORMATION).notify(project)
      return CompletableFuture.completedFuture(null)
    }
    val module = MavenProjectsManager.getInstance(project).findModule(mavenProject)
    if(module == null){
      Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP, "",
                   MavenProjectBundle.message("maven.quickfix.cannot.update.source.level.module.not.found", mavenProject.displayName),
                   NotificationType.INFORMATION).notify(project)
      return CompletableFuture.completedFuture(null)
    }

    val moduleJdk = MavenUtil.getModuleJdk(MavenProjectsManager.getInstance(project), mavenProject)
    if(moduleJdk == null){
      Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP, "",
                   MavenProjectBundle.message("maven.quickfix.cannot.update.source.level.module.not.found", mavenProject.displayName),
                   NotificationType.INFORMATION).notify(project)
      return CompletableFuture.completedFuture(null)
    }

    val promise = MavenProjectModelModifier(project).changeLanguageLevel(module, LanguageLevel.parse(moduleJdk.versionString)!!)
    if(promise == null) {
      return CompletableFuture.completedFuture(null)
    }
    OpenFileDescriptor(project, mavenProject.file).navigate(true)
    return promise.asCompletableFuture()
  }

  companion object {
    val ID = "maven_quickfix_source_level_"
  }

}