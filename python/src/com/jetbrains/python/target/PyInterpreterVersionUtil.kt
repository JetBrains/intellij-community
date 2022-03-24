// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("PyInterpreterVersionUtil")

package com.jetbrains.python.target

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.target.TargetProgressIndicatorAdapter
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.execution.target.getTargetEnvironmentRequest
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.remote.RemoteSdkException
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PyBundle

@Throws(RemoteSdkException::class)
fun PyTargetAwareAdditionalData.getInterpreterVersion(project: Project?, nullForUnparsableVersion: Boolean = true): String? {
  return getInterpreterVersion(project, interpreterPath, nullForUnparsableVersion)
}

@Throws(RemoteSdkException::class)
fun PyTargetAwareAdditionalData.getInterpreterVersion(project: Project?,
                                                      interpreterPath: String,
                                                      nullForUnparsableVersion: Boolean = true): String? {
  val targetEnvironmentRequest = getTargetEnvironmentRequest(project)
                                 ?: throw IllegalStateException("Unable to get target configuration from Python SDK data")
  val result = Ref.create<String>()
  val exception = Ref.create<RemoteSdkException>()
  val task: Task.Modal = object : Task.Modal(project, PyBundle.message("python.sdk.getting.remote.interpreter.version"), true) {
    override fun run(indicator: ProgressIndicator) {
      val flavor = flavor
      if (flavor != null) {
        try {
          try {
            val targetedCommandLineBuilder = TargetedCommandLineBuilder(targetEnvironmentRequest)
            targetedCommandLineBuilder.setExePath(interpreterPath)
            targetedCommandLineBuilder.addParameter(flavor.versionOption)
            val targetEnvironment = targetEnvironmentRequest.prepareEnvironment(TargetProgressIndicatorAdapter(indicator))
            val targetedCommandLine = targetedCommandLineBuilder.build()
            val process = targetEnvironment.createProcess(targetedCommandLine, indicator)
            val commandLineString = targetedCommandLine.collectCommandsSynchronously().joinToString(separator = " ")
            val capturingProcessHandler = CapturingProcessHandler(process, Charsets.UTF_8, commandLineString)
            val processOutput = capturingProcessHandler.runProcess()
            if (processOutput.exitCode == 0) {
              val version = flavor.getVersionStringFromOutput(processOutput)
              if (version != null || nullForUnparsableVersion) {
                result.set(version)
                return
              }
              else {
                throw RemoteSdkException(PyBundle.message("python.sdk.empty.version.string"), processOutput.stdout, processOutput.stderr)
              }
            }
            else {
              throw RemoteSdkException(
                PyBundle.message("python.sdk.non.zero.exit.code", processOutput.exitCode), processOutput.stdout, processOutput.stderr)
            }
          }
          catch (e: Exception) {
            throw RemoteSdkException.cantObtainRemoteCredentials(e)
          }
        }
        catch (e: RemoteSdkException) {
          exception.set(e)
        }
      }
    }
  }

  if (!ProgressManager.getInstance().hasProgressIndicator()) {
    UIUtil.invokeAndWaitIfNeeded(Runnable { ProgressManager.getInstance().run(task) })
  }
  else {
    task.run(ProgressManager.getInstance().progressIndicator)
  }

  if (!exception.isNull) {
    throw exception.get()
  }

  return result.get()
}