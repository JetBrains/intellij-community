// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes

import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.build.issue.quickfix.OpenFileQuickFix
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import org.jetbrains.idea.maven.externalSystemIntegration.output.LogMessageType
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLogEntryReader
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLoggedEventParser
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext
import org.jetbrains.idea.maven.externalSystemIntegration.output.importproject.MavenImportLoggedEventParser
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.server.MavenConfigParseException
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

class MavenBadConfigEventParser : MavenLoggedEventParser {
  override fun supportsType(type: LogMessageType?): Boolean {
    return type == null || type == LogMessageType.ERROR
  }

  override fun checkLogLine(parentId: Any,
                            parsingContext: MavenParsingContext,
                            logLine: MavenLogEntryReader.MavenLogEntry,
                            logEntryReader: MavenLogEntryReader,
                            messageConsumer: Consumer<in BuildEvent?>): Boolean {
    val line = logLine.line
    if (line.startsWith(MavenConfigBuildIssue.CONFIG_PARSE_ERROR) && logLine.type == null) {
      val buildIssue = MavenConfigBuildIssue.getIssue(
        line, line.substring(MavenConfigBuildIssue.CONFIG_PARSE_ERROR.length).trim(), parsingContext.ideaProject
      ) ?: return false
      messageConsumer.accept(
        BuildIssueEventImpl(parentId, buildIssue, MessageEvent.Kind.ERROR)
      )
      return true
    }
    if (line.startsWith(MavenConfigBuildIssue.CONFIG_VALUE_ERROR) && logLine.type == LogMessageType.ERROR) {
      val buildIssue = MavenConfigBuildIssue.getIssue(line, line, parsingContext.ideaProject) ?: return false
      messageConsumer.accept(
        BuildIssueEventImpl(parentId, buildIssue, MessageEvent.Kind.ERROR)
      )
      return true
    }

    return false
  }
}

class MavenImportBadConfigEventParser : MavenImportLoggedEventParser {

  override fun processLogLine(project: Project,
                              logLine: String,
                              reader: BuildOutputInstantReader?,
                              messageConsumer: Consumer<in BuildEvent>): Boolean {
    if (logLine.startsWith(MavenConfigBuildIssue.CONFIG_PARSE_ERROR)) {
      val buildIssue = MavenConfigBuildIssue.getIssue(
        logLine, logLine.substring(MavenConfigBuildIssue.CONFIG_PARSE_ERROR.length).trim(), project
      ) ?: return false
      messageConsumer.accept(
        BuildIssueEventImpl(Any(), buildIssue, MessageEvent.Kind.ERROR)
      )
      return true
    }
    if (logLine.startsWith(MavenConfigBuildIssue.CONFIG_VALUE_ERROR)) {
      val buildIssue = MavenConfigBuildIssue.getIssue(logLine, logLine, project) ?: return false
      messageConsumer.accept(
        BuildIssueEventImpl(Any(), buildIssue, MessageEvent.Kind.ERROR)
      )
      return true
    }

    return false
  }
}

class MavenConfigOpenQuickFix(private val mavenConfig: VirtualFile, val errorMessage: String) : BuildIssueQuickFix {

  override val id: String = "open_maven_config_quick_fix"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    var search: String? = null
    if (errorMessage.contains(":")) {
      search = errorMessage.substring(errorMessage.lastIndexOf(":"))
        .replace(":", "")
        .replace("\"", "")
        .replace("'", "")
        .trim()
    }
    OpenFileQuickFix.showFile(project, mavenConfig.toNioPath(), search)
    return CompletableFuture.completedFuture<Any>(null)
  }
}

object MavenConfigBuildIssue {
  const val CONFIG_PARSE_ERROR: String = "Unable to parse maven.config:"
  const val CONFIG_VALUE_ERROR: String = "For input string:"

  fun getIssue(ex: MavenConfigParseException): BuildIssue? {
    val configFile = LocalFileSystem.getInstance().findFileByPath(ex.directory)?.findFileByRelativePath(MavenConstants.MAVEN_CONFIG_RELATIVE_PATH)
    return configFile?.let { getConfigFile(it, ex.localizedMessage ?: ex.message ?: ex.stackTraceToString(), CONFIG_PARSE_ERROR) }

  }

  fun getIssue(title: String, errorMessage: String, project: Project): BuildIssue? {
    val mavenProject = MavenProjectsManager.getInstance(project).rootProjects.firstOrNull()
    if (mavenProject == null) {
      MavenLog.LOG.warn("Cannot find appropriate maven project,project =  ${project.name}")
      return null
    }
    val configFile = MavenUtil.getConfigFile(mavenProject, MavenConstants.MAVEN_CONFIG_RELATIVE_PATH)
    if (configFile == null) return null

    return getConfigFile(configFile, errorMessage, title)
  }

  private fun getConfigFile(configFile: VirtualFile,
                            errorMessage: String,
                            title: String): BuildIssue {
    val mavenConfigOpenQuickFix = MavenConfigOpenQuickFix(configFile, errorMessage)
    val quickFixes = listOf<BuildIssueQuickFix>(mavenConfigOpenQuickFix)
    val issueDescription = StringBuilder(errorMessage)
    issueDescription.append("\n\n")
    issueDescription.append(MavenProjectBundle.message("maven.quickfix.maven.config.file", mavenConfigOpenQuickFix.id))

    return object : BuildIssue {
      override val title: String = title
      override val description: String = issueDescription.toString()
      override val quickFixes = quickFixes
      override fun getNavigatable(project: Project): Navigatable? = null
    }
  }
}

