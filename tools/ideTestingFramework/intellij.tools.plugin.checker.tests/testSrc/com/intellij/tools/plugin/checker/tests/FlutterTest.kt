package com.intellij.tools.plugin.checker.tests

import com.intellij.ide.starter.ide.command.CommandChain
import com.intellij.ide.starter.runner.TestContainerImpl
import com.intellij.tools.plugin.checker.data.TestCases
import com.intellij.tools.plugin.checker.junit5.JUnit5StarterAssistant
import com.intellij.tools.plugin.checker.junit5.hyphenateWithClass
import com.jetbrains.performancePlugin.commands.chain.exitApp
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(JUnit5StarterAssistant::class)
class FlutterTest {
  private lateinit var testInfo: TestInfo
  private lateinit var context: TestContainerImpl

  @Test
  fun openFlutterProject() {

    // TODO: prepare a correct project for this plugin
    val testContext = context
      .initializeTestContext(testInfo.hyphenateWithClass(), TestCases.IC.MavenSimpleApp)
      .prepareProjectCleanImport()
      .setSharedIndexesDownload(enable = true)

    testContext.runIDE(commands = CommandChain().exitApp())
  }
}