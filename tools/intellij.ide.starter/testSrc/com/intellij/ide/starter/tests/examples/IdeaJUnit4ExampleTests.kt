package com.intellij.ide.starter.tests.examples

import com.intellij.ide.starter.ide.command.CommandChain
import com.intellij.ide.starter.sdk.JdkDownloaderFacade
import com.intellij.ide.starter.tests.examples.data.TestCases
import com.intellij.ide.starter.tests.examples.junit4.hyphenateWithClass
import com.intellij.ide.starter.tests.examples.junit4.initStarterRule
import com.jetbrains.performancePlugin.commands.chain.exitApp
//import com.jetbrains.performancePlugin.commands.setupSdk
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName

class IdeaJUnit4ExampleTests {
  @get:Rule
  val testName = TestName()

  @get:Rule
  val testContextFactory = initStarterRule()

  private val sdk17 by lazy {
    JdkDownloaderFacade.jdk17.toSdk("17")
  }

  @Test
  fun `open gradle project on the latest EAP IJ Community`() {
    val context = testContextFactory
      .initializeTestRunner(testName.hyphenateWithClass(this::class), TestCases.IC.GradleJitPackSimple)
      // TODO: uncomment after 04.07.2022
      //.setupSdk(sdk17)
      .prepareProjectCleanImport()
      .skipIndicesInitialization()
      .setSharedIndexesDownload(enable = true)

    context.runIDE(commands = CommandChain().exitApp())
  }

  @Test
  fun `open gradle project on the latest Release IJ Community`() {
    val context = testContextFactory
      .initializeTestRunner(testName.hyphenateWithClass(this::class), TestCases.IC.GradleJitPackSimple.useRelease())
      .prepareProjectCleanImport()
      .skipIndicesInitialization()
      .setSharedIndexesDownload(enable = true)

    context.runIDE(commands = CommandChain().exitApp())
  }
}