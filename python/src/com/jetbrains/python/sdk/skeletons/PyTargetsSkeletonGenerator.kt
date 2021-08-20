// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.skeletons

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.execution.target.value.getTargetDownloadPath
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.io.FileUtil
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.run.buildTargetedCommandLine
import com.jetbrains.python.run.prepareHelperScriptExecution
import com.jetbrains.python.sdk.InvalidSdkException
import com.jetbrains.python.sdk.skeleton.PySkeletonHeader
import java.nio.file.Paths

class PyTargetsSkeletonGenerator(skeletonPath: String?, pySdk: Sdk, currentFolder: String?)
  : PySkeletonGenerator(skeletonPath, pySdk, currentFolder) {
  private val myTargetEnvRequest = checkNotNull(PythonInterpreterTargetEnvironmentFactory.findTargetEnvironmentRequest(sdk = mySdk))
  private val myFoundBinaries: MutableSet<String> = HashSet()

  private fun isLocalTarget() = myTargetEnvRequest is LocalTargetEnvironmentRequest

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
      val generatorScriptExecution = prepareHelperScriptExecution(helperPackage = PythonHelper.GENERATOR3,
        targetEnvironmentRequest = myTargetEnvRequest)
      generatorScriptExecution.addParameter("-d")
      val skeletonsDownloadRoot = TargetEnvironment.DownloadRoot(localRootPath = Paths.get(mySkeletonsPath),
        targetRootPath = TargetEnvironment.TargetPath.Temporary())
      myTargetEnvRequest.downloadVolumes += skeletonsDownloadRoot
      generatorScriptExecution.addParameter(skeletonsDownloadRoot.getTargetDownloadPath())
      if (myAssemblyRefs.isNotEmpty()) {
        generatorScriptExecution.addParameter("-c")
        // TODO [targets-api] these refs are paths or some strings?
        generatorScriptExecution.addParameter(myAssemblyRefs.joinToString(separator = ";"))
      }
      if (myExtraSysPath.isNotEmpty()) {
        generatorScriptExecution.addParameter("-s")
        // TODO [targets-api] are these paths come from target or from the local machine?
        val pathSeparatorOnTarget = myTargetEnvRequest.targetPlatform.platform.pathSeparator
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
      val targetEnvironment = myTargetEnvRequest.prepareEnvironment(TargetProgressIndicator.EMPTY)
      val targetedCommandLine = generatorScriptExecution.buildTargetedCommandLine(targetEnvironment, mySdk, emptyList())
      val process = targetEnvironment.createProcess(targetedCommandLine, EmptyProgressIndicator())
      val commandPresentation = targetedCommandLine.getCommandPresentation(targetEnvironment)
      val capturingProcessHandler = CapturingProcessHandler(process, targetedCommandLine.charset, commandPresentation)
      listener?.let { capturingProcessHandler.addProcessListener(LineWiseProcessOutputListener.Adapter(it)) }
      val result = capturingProcessHandler.runProcess()
      targetEnvironment.downloadVolumes.values.forEach { it.download(".", EmptyProgressIndicator()) }
      return result
    }
  }

  override fun runGeneration(builder: Builder, indicator: ProgressIndicator?): MutableList<GenerationResult> {
    myFoundBinaries.clear()
    val results = super.runGeneration(builder, indicator)
    results.asSequence()
      .map { it.moduleOrigin }
      .filter { PySkeletonHeader.BUILTIN_NAME != it }
      .toCollection(myFoundBinaries)
    return results
  }

  override fun exists(name: String): Boolean {
    if (isLocalTarget()) {
      return FileUtil.exists(name)
    }
    else {
      return name in myFoundBinaries
    }
  }
}