// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.skeletons

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironmentAwareRunProfileState.TargetProgressIndicator
import com.intellij.execution.target.value.getTargetDownloadPath
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.run.buildTargetedCommandLine
import com.jetbrains.python.run.prepareHelperScriptExecution
import com.jetbrains.python.sdk.InvalidSdkException
import java.nio.file.Paths

class PyTargetsSkeletonGenerator(skeletonPath: String?, pySdk: Sdk, currentFolder: String?)
  : PySkeletonGenerator(skeletonPath, pySdk, currentFolder) {
  override fun commandBuilder(): Builder {
    val builder = TargetedBuilder()
    myCurrentFolder?.let { builder.workingDir(it) }
    return builder
  }

  private inner class TargetedBuilder : Builder() {
    override fun runProcessWithLineOutputListener(listener: LineWiseProcessOutputListener): ProcessOutput = doRunProcess(listener)

    @Throws(InvalidSdkException::class)
    override fun runProcess(): ProcessOutput = doRunProcess(listener = null)

    private fun doRunProcess(listener: LineWiseProcessOutputListener?): ProcessOutput {
      val targetEnvironmentFactory = PythonInterpreterTargetEnvironmentFactory.findTargetEnvironmentFactory(sdk = mySdk)
                                     ?: throw InvalidSdkException("The factory is not registered for Python SDK $mySdk")
      val request = targetEnvironmentFactory.createRequest()
      val generatorScriptExecution = prepareHelperScriptExecution(helperPackage = PythonHelper.GENERATOR3,
                                                                  targetEnvironmentRequest = request)
      generatorScriptExecution.addParameter("-d")
      val skeletonsDownloadRoot = TargetEnvironment.DownloadRoot(localRootPath = Paths.get(mySkeletonsPath),
                                                                 targetRootPath = TargetEnvironment.TargetPath.Temporary())
      request.downloadVolumes += skeletonsDownloadRoot
      generatorScriptExecution.addParameter(skeletonsDownloadRoot.getTargetDownloadPath())
      if (myAssemblyRefs.isNotEmpty()) {
        generatorScriptExecution.addParameter("-c")
        // TODO [targets-api] these refs are paths or some strings?
        generatorScriptExecution.addParameter(myAssemblyRefs.joinToString(separator = ";"))
      }
      if (myExtraSysPath.isNotEmpty()) {
        generatorScriptExecution.addParameter("-s")
        // TODO [targets-api] are these paths come from target or from the local machine?
        val pathSeparatorOnTarget = targetEnvironmentFactory.targetPlatform.platform.pathSeparator
        generatorScriptExecution.addParameter(myExtraSysPath.joinToString(separator = pathSeparatorOnTarget.toString()))
      }
      for (extraArg in myExtraArgs) {
        generatorScriptExecution.addParameter(extraArg)
      }
      if (!myTargetModuleName.isNullOrEmpty()) {
        generatorScriptExecution.addParameter(myTargetModuleName)
        // TODO [targets-api] is this path target-specific or local-specific?
        if (!myTargetModulePath.isNullOrEmpty()) {
          generatorScriptExecution.addParameter(myTargetModulePath)
        }
      }
      val targetEnvironment = targetEnvironmentFactory.prepareRemoteEnvironment(request, TargetProgressIndicator.EMPTY)
      val targetedCommandLine = generatorScriptExecution.buildTargetedCommandLine(targetEnvironment, mySdk, emptyList())
      val process = targetEnvironment.createProcess(targetedCommandLine, EmptyProgressIndicator())
      val commandPresentation = targetedCommandLine.getCommandPresentation(targetEnvironment)
      val capturingProcessHandler = CapturingProcessHandler(process, targetedCommandLine.charset, commandPresentation)
      listener?.let { capturingProcessHandler.addProcessListener(LineWiseProcessOutputListener.Adapter(it)) }
      return capturingProcessHandler.runProcess()
    }
  }
}