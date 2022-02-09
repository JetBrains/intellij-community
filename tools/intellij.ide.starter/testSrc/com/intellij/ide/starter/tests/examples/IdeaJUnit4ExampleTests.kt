package com.intellij.ide.starter.tests.examples

import com.intellij.ide.starter.ide.command.CommandChain
import com.intellij.ide.starter.tests.examples.data.TestCases
import com.intellij.ide.starter.tests.examples.junit4.initStarterRule
import com.intellij.ide.starter.tests.examples.junit4.toPrintableWithClass
import com.jetbrains.performancePlugin.commands.chain.exitApp
import com.jetbrains.performancePlugin.gradle.commands.importGradleProject
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName

class IdeaJUnit4ExampleTests {
  @get:Rule
  val testName = TestName()

  @get:Rule
  val testContextFactory = initStarterRule()

  @Test
  fun openProjectExampleTest() {
    val context = testContextFactory
      .initializeTestRunner(testName.toPrintableWithClass(this::class), TestCases.IJ.GradleJitPackSimple)
      .prepareProjectCleanImport()
      .disableAutoImport()

    context.runIDE(
      commands = CommandChain()
        .importGradleProject()
        .exitApp()
    )
  }
}