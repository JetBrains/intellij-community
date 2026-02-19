// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes

import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.build.issue.quickfix.OpenFileQuickFix
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.impl.RunDialog
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import org.jetbrains.idea.maven.buildtool.quickfix.OpenMavenImportingSettingsQuickFix
import org.jetbrains.idea.maven.buildtool.quickfix.OpenMavenRunnerSettingsQuickFix
import org.jetbrains.idea.maven.execution.MavenExternalParameters
import org.jetbrains.idea.maven.execution.MavenRunConfiguration
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType
import org.jetbrains.idea.maven.externalSystemIntegration.output.LogMessageType
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLogEntryReader
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLoggedEventParser
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext
import org.jetbrains.idea.maven.externalSystemIntegration.output.importproject.MavenImportLoggedEventParser
import org.jetbrains.idea.maven.project.MavenConfigurableBundle.message
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.server.MavenDistributionsCache
import java.nio.file.Paths
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
      val causeLine = logEntryReader.readLine()?.line ?: ""
      if (causeLine.isNotEmpty()) {
        errorLine += "\n$causeLine"
      }
      messageConsumer.accept(
        BuildIssueEventImpl(
          parentId,
          MavenJvmConfigBuildIssue.getRunnerIssue(logLine.line, errorLine, parsingContext.ideaProject, parsingContext.runConfiguration),
          MessageEvent.Kind.ERROR
        )
      )
      return true
    }

    return false
  }
}

class MavenImportBadJvmConfigEventParser : MavenImportLoggedEventParser {

  override fun processLogLine(project: Project,
                              logLine: String,
                              reader: BuildOutputInstantReader?,
                              messageConsumer: Consumer<in BuildEvent>): Boolean {
    var errorLine = logLine
    if (logLine.startsWith(MavenJvmConfigBuildIssue.VM_INIT_ERROR)) {
      val causeLine = reader?.readLine() ?: ""
      if (causeLine.isNotEmpty()) {
        errorLine += "\n$causeLine"
      }
      messageConsumer.accept(BuildIssueEventImpl(Any(),
                                                 MavenJvmConfigBuildIssue.getImportIssue(logLine, errorLine, project),
                                                 MessageEvent.Kind.ERROR))
      return true
    }

    return false
  }
}

class MavenJvmConfigOpenQuickFix(private val jvmConfig: VirtualFile) : BuildIssueQuickFix {

  override val id: String = "open_maven_jvm_config_quick_fix_$jvmConfig"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    OpenFileQuickFix.showFile(project, jvmConfig.toNioPath(), null)
    return CompletableFuture.completedFuture<Any>(null)
  }
}

class MavenRunConfigurationOpenQuickFix(private val runnerAndConfigurationSettings: RunnerAndConfigurationSettings) : BuildIssueQuickFix {

  override val id: String = "open_maven_run_configuration_open_quick_fix"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    RunDialog.editConfiguration(project, runnerAndConfigurationSettings, message("maven.settings.runner.vm.options"))
    return CompletableFuture.completedFuture<Any>(null)
  }
}

object MavenJvmConfigBuildIssue {
  const val VM_INIT_ERROR: String = "Error occurred during initialization of VM"

  fun getRunnerIssue(title: String, errorMessage: String, project: Project, runConfiguration: MavenRunConfiguration): BuildIssue {
    val jvmConfig: VirtualFile? = MavenExternalParameters.getJvmConfig(runConfiguration.runnerParameters.workingDirPath)
    val mavenProject = MavenProjectsManager.getInstance(project).projectsFiles
      .asSequence()
      .filter { Paths.get(runConfiguration.runnerParameters.workingDirPath).equals(it.toNioPath().parent) }
      .map { MavenProjectsManager.getInstance(project).findProject(it) }
      .firstOrNull()

    val quickFixes = mutableListOf<BuildIssueQuickFix>()
    val issueDescription = StringBuilder(errorMessage)
    issueDescription.append("\n\n")
    issueDescription.append(MavenProjectBundle.message("maven.quickfix.header.possible.solution"))
    issueDescription.append("\n")

    val openMavenRunnerSettingsQuickFix = OpenMavenRunnerSettingsQuickFix(message("maven.settings.runner.vm.options"))
    quickFixes.add(openMavenRunnerSettingsQuickFix)
    issueDescription.append(MavenProjectBundle.message("maven.quickfix.jvm.options.runner.settings", openMavenRunnerSettingsQuickFix.id))
    issueDescription.append("\n")

    val configurationById = runConfiguration
      .let { RunManagerImpl.getInstanceImpl(project).findConfigurationByTypeAndName(MavenRunConfigurationType.getInstance(), it.name) }
    if (configurationById != null && configurationById.configuration is MavenRunConfiguration) {
      val configuration = configurationById.configuration as MavenRunConfiguration
      if (!configuration.runnerSettings?.vmOptions.isNullOrBlank()) {
        val mavenRunConfigurationOpenQuickFix = MavenRunConfigurationOpenQuickFix(configurationById)
        quickFixes.add(mavenRunConfigurationOpenQuickFix)
        issueDescription.append(MavenProjectBundle
                                  .message("maven.quickfix.jvm.options.run.configuration", mavenRunConfigurationOpenQuickFix.id))
        issueDescription.append("\n")
      }
    }

    if (jvmConfig != null) {
      val mavenJvmConfigOpenQuickFix = MavenJvmConfigOpenQuickFix(jvmConfig)
      quickFixes.add(mavenJvmConfigOpenQuickFix)
      issueDescription.append(MavenProjectBundle.message("maven.quickfix.jvm.options.config.file",
                                                         mavenProject?.displayName ?: "",
                                                         mavenJvmConfigOpenQuickFix.id))
    }

    return object : BuildIssue {
      override val title: String = title
      override val description: String = issueDescription.toString()
      override val quickFixes = quickFixes
      override fun getNavigatable(project: Project): Navigatable? = null
    }
  }

  fun getImportIssue(title: String, errorMessage: String, project: Project): BuildIssue {
    val quickFixes = mutableListOf<BuildIssueQuickFix>()
    val issueDescription = StringBuilder(errorMessage)
    issueDescription.append("\n\n")
    issueDescription.append(MavenProjectBundle.message("maven.quickfix.header.possible.solution"))
    issueDescription.append("\n")

    val openMavenImportingSettingsQuickFix = OpenMavenImportingSettingsQuickFix(message("maven.settings.importing.vm.options"))
    quickFixes.add(openMavenImportingSettingsQuickFix)
    issueDescription.append(
      MavenProjectBundle.message("maven.quickfix.jvm.options.import.settings", openMavenImportingSettingsQuickFix.id))

    MavenProjectsManager.getInstance(project).rootProjects.forEach {
      val jvmConfig: VirtualFile? = it.file.path
        .let { MavenDistributionsCache.getInstance(project).getMultimoduleDirectory(it) }
        .let { MavenExternalParameters.getJvmConfig(it) }
      if (jvmConfig != null) {
        val mavenJvmConfigOpenQuickFix = MavenJvmConfigOpenQuickFix(jvmConfig)
        quickFixes.add(mavenJvmConfigOpenQuickFix)
        issueDescription.append("\n")
        issueDescription.append(MavenProjectBundle
                                  .message("maven.quickfix.jvm.options.config.file", it.displayName, mavenJvmConfigOpenQuickFix.id))
      }
    }

    return object : BuildIssue {
      override val title: String = title
      override val description: String = issueDescription.toString()
      override val quickFixes = quickFixes
      override fun getNavigatable(project: Project): Navigatable? = null
    }
  }
}