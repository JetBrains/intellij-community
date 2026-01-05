package com.intellij.ide.starter.driver

import com.intellij.driver.client.Driver
import com.intellij.driver.client.service
import com.intellij.driver.sdk.DriverTestLogger
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.setupOrDetectSdk
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.waitForProjectOpen
import com.intellij.ide.starter.driver.engine.LogColor
import com.intellij.ide.starter.driver.engine.color
import com.intellij.ide.starter.report.AllureHelper
import com.intellij.ide.starter.report.AllureHelper.step
import com.intellij.ide.starter.telemetry.computeWithSpan
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.SdkObject
import com.intellij.tools.ide.util.common.logOutput
import kotlin.io.path.absolutePathString

fun Driver.execute(commands: CommandChain, project: Project? = null) {
  waitForProjectOpen()
  for (cmd in commands) {
    val commandString = cmd.storeToString()
    computeWithSpan("execute command") { span ->
      span.setAttribute("commands", commandString)
      val split = commandString.split(" ")
      val stepName = "Execute command " + split.first()
      step(stepName) {
        if (split.size > 1) {
          AllureHelper.attachText("Params", split.drop(1).joinToString(" "))
        }
        service<PlaybackRunnerService>().runScript(project ?: singleProject(), commandString)
      }
    }
  }
}

fun Driver.execute(project: Project? = null, commands: (CommandChain) -> CommandChain) {
  execute(project = project, commands = commands(CommandChain()))
}

fun Driver.setupOrDetectSdk(sdk: SdkObject) {
  setupOrDetectSdk(sdk.sdkName, sdk.sdkType, sdk.sdkPath.absolutePathString())
}

fun Driver.setupOrDetectSdk(project: Project, sdk: SdkObject) {
  setupOrDetectSdk(project, sdk.sdkName, sdk.sdkType, sdk.sdkPath.absolutePathString())
}

fun <T> DriverTestLogger.run(text: String, action: () -> T): T = try {
  val startedText = "$text started"
  logOutput(startedText.color(LogColor.GREEN))
  info(startedText)
  val actionStarted = System.currentTimeMillis()
  val result = action()
  val time = System.currentTimeMillis() - actionStarted
  val finishedText = "$text finished in ${time}ms"
  logOutput(finishedText.color(LogColor.GREEN))
  runCatching { info(finishedText) }
  result
} catch (e: Throwable) {
  runCatching { warn("$text failed with '${e.message}'") }
  throw e
}