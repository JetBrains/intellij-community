package com.intellij.ide.starter.runner

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.utils.FileSystem
import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.starter.bus.events.Event
import com.intellij.tools.ide.util.common.logOutput


class ValidateVMOptionsWereSetEvent(runContext: IDERunContext) : Event()

internal fun validateVMOptionsWereSet(runContext: IDERunContext) {
  EventsBus.postAndWaitProcessing(ValidateVMOptionsWereSetEvent(runContext))

  logOutput("Run VM options validation")

  if (!FileSystem.hasAtLeastFiles(runContext.testContext.paths.configDir, 4)) {
    CIServer.instance.reportTestFailure(
      testName = "IDE must have created files under config directory at ${runContext.testContext.paths.configDir}. Were .vmoptions included correctly?",
      message = "", details = "")
  }

  if (!FileSystem.hasAtLeastFiles(runContext.testContext.paths.systemDir, 2)) {
    CIServer.instance.reportTestFailure(
      testName = "IDE must have created files under system directory at ${runContext.testContext.paths.systemDir}. Were .vmoptions included correctly?",
      message = "", details = "")
  }

  logOutput("Finished VM options validation")
}