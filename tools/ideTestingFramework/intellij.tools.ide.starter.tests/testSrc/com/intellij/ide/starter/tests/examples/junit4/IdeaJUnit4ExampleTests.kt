package com.intellij.ide.starter.tests.examples.junit4

import com.intellij.ide.starter.ide.command.CommandChain
import com.intellij.ide.starter.junit4.hyphenateWithClass
import com.intellij.ide.starter.junit4.initStarterRule
import com.intellij.ide.starter.sdk.JdkDownloaderFacade
import com.intellij.ide.starter.sdk.JdkVersion
import com.intellij.ide.starter.tests.examples.data.TestCases
import com.jetbrains.performancePlugin.commands.chain.exitApp
import com.jetbrains.performancePlugin.commands.setupSdk
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName

class IdeaJUnit4ExampleTests {
  @get:Rule
  val testName = TestName()

  @get:Rule
  val testContextFactory = initStarterRule()

  private val sdk17 by lazy {
    JdkDownloaderFacade.jdk17.toSdk(JdkVersion.JDK_17)
  }

  @Test
  fun `open gradle project on the latest EAP IJ Community`() {
    val context = testContextFactory
      .initializeTestContext(testName.hyphenateWithClass(this::class), TestCases.IC.GradleJitPackSimple)
      .setupSdk(sdk17)
      .prepareProjectCleanImport()
      .skipIndicesInitialization()
      .setSharedIndexesDownload(enable = true)

    context.runIDE(commands = CommandChain().exitApp())
  }

  @Test
  fun `open gradle project on the latest Release IJ Community`() {
    val context = testContextFactory
      .initializeTestContext(testName.hyphenateWithClass(this::class), TestCases.IC.GradleJitPackSimple.useRelease())
      .prepareProjectCleanImport()
      .skipIndicesInitialization()
      .setSharedIndexesDownload(enable = true)

    context.runIDE(commands = CommandChain().exitApp())
  }
}