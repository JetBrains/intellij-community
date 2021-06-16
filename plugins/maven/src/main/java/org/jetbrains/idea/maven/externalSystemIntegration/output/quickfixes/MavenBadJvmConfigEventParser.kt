// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes

import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.build.issue.quickfix.OpenFileQuickFix
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.execution.impl.EditConfigurationsDialog
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import org.apache.commons.lang.StringUtils
import org.jetbrains.idea.maven.buildtool.quickfix.OpenMavenImportingSettingsQuickFix
import org.jetbrains.idea.maven.buildtool.quickfix.OpenMavenRunnerSettingsQuickFix
import org.jetbrains.idea.maven.execution.MavenExternalParameters
import org.jetbrains.idea.maven.execution.MavenRunConfiguration
import org.jetbrains.idea.maven.externalSystemIntegration.output.LogMessageType
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLogEntryReader
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLoggedEventParser
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext
import org.jetbrains.idea.maven.externalSystemIntegration.output.importproject.MavenImportLoggedEventParser
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.server.MavenDistributionsCache
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

class MavenBadJvmConfigEventParser : MavenLoggedEventParser {
  override fun supportsType(type: LogMessageType?): Boolean {
    return type == null
  }

  override fun checkLogLine(parentId: Any,
                            parsingContext: MavenParsingContext,
                            logLine: MavenLogEntryReader.MavenLogEntry,
                            logEntryReader: MavenLogEntryReader,
                            messageConsumer: Consumer<in BuildEvent?>): Boolean {
    var errorLine = logLine.line
    if (errorLine.startsWith(MavenJvmConfigBuildIssue.VM_INIT_ERROR)) {
      val causeLine = logEntryReader.readLine()?.line ?: StringUtils.EMPTY
      if (causeLine.isNotEmpty()) {
        errorLine += "\n$causeLine"
      }
      messageConsumer.accept(
        BuildIssueEventImpl(parentId,
                            MavenJvmConfigBuildIssue.getRunnerIssue(logLine.line, errorLine, parsingContext.ideaProject),
                            MessageEvent.Kind.ERROR)
      )
      return true
    }

    return false
  }
}

class MavenImportBadJvmConfigEventParser : MavenImportLoggedEventParser {

  override fun processLogLine(project: Project,
                              logLine: String,
                              reader: BuildOutputInstantReader,
                              messageConsumer: Consumer<in BuildEvent>): Boolean {
    var errorLine = logLine
    if (logLine.startsWith(MavenJvmConfigBuildIssue.VM_INIT_ERROR)) {
      val causeLine = reader.readLine() ?: StringUtils.EMPTY
      if (causeLine.isNotEmpty()) {
        errorLine += "\n$causeLine"
      }
      messageConsumer.accept(BuildIssueEventImpl(Object(),
                                              MavenJvmConfigBuildIssue.getImportIssue(logLine, errorLine, project),
                                              MessageEvent.Kind.ERROR))
      return true
    }

    return false
  }
}

class MavenJvmConfigOpenQuickFix(val jvmConfig: VirtualFile) : BuildIssueQuickFix {

  override val id: String = "open_maven_jvm_config_quick_fix"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    OpenFileQuickFix.showFile(project, jvmConfig.toNioPath(), null)
    return CompletableFuture.completedFuture<Any>(null)
  }
}

class MavenRunConfigurationOpenQuickFix() : BuildIssueQuickFix {

  override val id: String = "open_maven_run_configuration_open_quick_fix"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    EditConfigurationsDialog(project).show()
    return CompletableFuture.completedFuture<Any>(null)
  }
}

object MavenJvmConfigBuildIssue {
  const val VM_INIT_ERROR:String = "Error occurred during initialization of VM"

  fun getRunnerIssue(title: String, errorMessage: String, project: Project) = getBuildIssue(title, errorMessage, project, false)

  fun getImportIssue(title: String, errorMessage: String, project: Project) = getBuildIssue(title, errorMessage, project, true)

  private fun getBuildIssue(title: String, errorMessage: String, project: Project, import: Boolean): BuildIssue {
    val rootMavenProject = MavenProjectsManager.getInstance(project).rootProjects.firstOrNull()
    val jvmConfig: VirtualFile? = rootMavenProject?.file?.path
      ?.let { MavenDistributionsCache.getInstance(project).getMultimoduleDirectory(it) }
      ?.let { MavenExternalParameters.getJvmConfig(it) }

    val quickFixes = mutableListOf<BuildIssueQuickFix>()
    val issueDescription = StringBuilder(errorMessage)
    issueDescription.append("\n\nPossible solution:\n")
    if (import) {
      val openMavenImportingSettingsQuickFix = OpenMavenImportingSettingsQuickFix("VM options for importer")
      quickFixes.add(openMavenImportingSettingsQuickFix)
      issueDescription.append(
        " - Check your maven import VM options. <a href=\"${openMavenImportingSettingsQuickFix.id}\">Open maven import settings</a>.\n")
    }
    else {
      val openMavenRunnerSettingsQuickFix = OpenMavenRunnerSettingsQuickFix("VM Options")
      quickFixes.add(openMavenRunnerSettingsQuickFix)
      issueDescription.append(
        " - Check your maven runner VM options. <a href=\"${openMavenRunnerSettingsQuickFix.id}\">Open maven runner settings</a>.\n")
      val selectedConfiguration = RunManagerImpl.getInstanceImpl(project).selectedConfiguration
      if (selectedConfiguration != null && selectedConfiguration.configuration is MavenRunConfiguration) {
        val mavenRunConfigurationOpenQuickFix = MavenRunConfigurationOpenQuickFix()
        quickFixes.add(mavenRunConfigurationOpenQuickFix)
        issueDescription.append(
          " - Check your maven run configuration. Tab runner - VM options. <a href=\"${mavenRunConfigurationOpenQuickFix.id}\">Open maven run configuration</a>.\n")
      }
    }
    if (jvmConfig != null) {
      val mavenJvmConfigOpenQuickFix = MavenJvmConfigOpenQuickFix(jvmConfig)
      quickFixes.add(mavenJvmConfigOpenQuickFix)
      issueDescription.append(
        " - Check your .mvn/jvm.config. <a href=\"${mavenJvmConfigOpenQuickFix.id}\">Open jvm config.</a>\n")
    }

    return object : BuildIssue {
      override val title: String = title
      override val description: String = issueDescription.toString()
      override val quickFixes = quickFixes
      override fun getNavigatable(project: Project): Navigatable? = null
    }
  }
}

