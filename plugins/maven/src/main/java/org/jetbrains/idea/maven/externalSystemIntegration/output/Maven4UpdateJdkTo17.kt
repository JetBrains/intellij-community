// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output

import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildEventsNls
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.pom.Navigatable
import org.jetbrains.idea.maven.buildtool.quickfix.ChooseAnotherJdkQuickFix
import org.jetbrains.idea.maven.execution.MavenRunConfiguration
import org.jetbrains.idea.maven.execution.RunnerBundle
import java.util.function.Consumer

class Maven4UpdateJdkTo17 : MavenLoggedEventParser {
  override fun supportsType(type: LogMessageType?): Boolean {
    return type == null
  }


  override fun checkLogLine(parentId: Any, parsingContext: MavenParsingContext, logLine: MavenLogEntryReader.MavenLogEntry, logEntryReader: MavenLogEntryReader, messageConsumer: Consumer<in BuildEvent>): Boolean {
    val line = logLine.line
    if (line.startsWith(PREFIX)) {
      @Suppress("HardCodedStringLiteral")
      val concatenated = (listOf(line) + logEntryReader.readWhile { it.type == null }.map { it.line })
        .joinToString("<br/>")
      val event = BuildIssueEventImpl(
        parentId,
        WrongRunnerJdkVersion(concatenated, parsingContext.runConfiguration),
        MessageEvent.Kind.ERROR
      )
      messageConsumer.accept(event)
      return true
    }
    return false
  }

  companion object {
    private const val PREFIX = "Error: Apache Maven 4.x requires Java 17 or newer to run"
  }
}

private class WrongRunnerJdkVersion(val mvnMessage: @NlsSafe String, val runConfiguration: MavenRunConfiguration) : BuildIssue {
  override val title: @BuildEventsNls.Title String
    get() = RunnerBundle.message("maven.4.old.jdk")

  @Suppress("HardCodedStringLiteral")
  override val description: @BuildEventsNls.Description String
    get() = mvnMessage + "\n<br/>" + RunnerBundle.message("maven.4.old.jdk.modify.config.quick.fix", ChooseAnotherJdkQuickFix.ID, OpenRunConfigurationQuickFix.ID)
  override val quickFixes: List<BuildIssueQuickFix> = listOf(
    ChooseAnotherJdkQuickFix()
  )

  override fun getNavigatable(project: Project): Navigatable? = null

}
