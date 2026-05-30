package com.jetbrains.performancePlugin.freezes

import com.intellij.performanceTesting.freezes.diogen.FreezeTitleGenerator
import com.intellij.performanceTesting.freezes.diogen.ThreadDumpParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class FreezeTitleGeneratorTest {
  @Test
  fun githubCopilotReadActionIsFreezeCause() {
    val resource = "dumps/2044695425478561792-github-copilot.txt"
    val dump = javaClass.getResource(resource)?.readText()
               ?: error("Missing classpath resource next to ${javaClass.name}: $resource")

    val cause = ThreadDumpParser.getFreezeCauseThread(ThreadDumpParser.getStackTraces(dump))
    assertNotNull("Freeze cause thread must be detected in the Copilot dump", cause)

    assertEquals(
      $$"com.github.copilot.lsp.LSPManager.doNotifyDidOpen$Lambda",
      FreezeTitleGenerator.formatTitle(FreezeTitleGenerator.selectCallable(cause!!))
    )
  }

  @Test
  fun githubCopilotAwtOnBackgroundFreezeCause() {
    val resource = "dumps/2033555930926551040-github-copilot.txt"
    val dump = javaClass.getResource(resource)?.readText()
               ?: error("Missing classpath resource next to ${javaClass.name}: $resource")

    val cause = ThreadDumpParser.getFreezeCauseThread(ThreadDumpParser.getStackTraces(dump))
    assertNotNull("Freeze cause thread must be detected in the Copilot dump", cause)

    assertEquals(
      $$"com.github.copilot.agent.message.codeblock.CodeBlock.initEditorTextField$Lambda",
      FreezeTitleGenerator.formatTitle(FreezeTitleGenerator.selectCallable(cause!!))
    )
  }

  @Test
  fun azureReadActionFreezeCause() {
    val resource = "dumps/2053394179475902464-azure.txt"
    val dump = javaClass.getResource(resource)?.readText()
               ?: error("Missing classpath resource next to ${javaClass.name}: $resource")

    val cause = ThreadDumpParser.getFreezeCauseThread(ThreadDumpParser.getStackTraces(dump))
    assertNotNull("Freeze cause thread must be detected in the Azure dump", cause)

    assertEquals(
      $$"com.microsoft.azure.toolkit.intellij.java.sdk.MavenProjectReportGenerator.lambda$execute$1$Lambda",
      FreezeTitleGenerator.formatTitle(FreezeTitleGenerator.selectCallable(cause!!))
    )
  }
}
