// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.black

import com.intellij.execution.process.CapturingProcessAdapter
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.target.*
import com.intellij.execution.target.local.LocalTargetEnvironment
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Version
import com.jetbrains.python.PyBundle
import com.jetbrains.python.black.configuration.BlackFormatterConfiguration
import com.jetbrains.python.pyi.PyiFileType
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.sdk.InvalidSdkException
import com.jetbrains.python.sdk.configureBuilderToRunPythonOnTarget
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class BlackFormatterExecutor(private val project: Project,
                             private val sdk: Sdk,
                             private val blackConfig: BlackFormatterConfiguration) {

  private var targetEnvironment: TargetEnvironment
  private var targetEnvironmentRequest: TargetEnvironmentRequest

  companion object {
    val BLACK_DEFAULT_TIMEOUT: Duration = 30000.milliseconds
    private val LOG = thisLogger()
    private val minimalStdinFilenameCompatibleVersion = Version(21, 4, 0)
  }

  init {
    when (blackConfig.executionMode) {
      BlackFormatterConfiguration.ExecutionMode.BINARY -> {
        targetEnvironmentRequest = LocalTargetEnvironmentRequest()
        targetEnvironment = LocalTargetEnvironment(LocalTargetEnvironmentRequest())
      }
      BlackFormatterConfiguration.ExecutionMode.PACKAGE -> {
        if (!sdk.sdkType.isLocalSdk(sdk)) {
          throw InvalidSdkException(PyBundle.message("black.remote.sdk.exception.text"))
        }
        val interpreter = PythonInterpreterTargetEnvironmentFactory.findPythonTargetInterpreter(sdk, project)
        targetEnvironmentRequest = interpreter.targetEnvironmentRequest
        targetEnvironment = targetEnvironmentRequest.prepareEnvironment(TargetProgressIndicator.EMPTY)
      }
    }
  }


  fun getBlackFormattingResponse(blackFormattingRequest: BlackFormattingRequest,
                                 timeout: Duration): BlackFormattingResponse {
    val targetCMD = buildTargetCommandLine(blackFormattingRequest, targetEnvironmentRequest)
    val future = getFuture(targetCMD, targetEnvironment, blackFormattingRequest, timeout)
    return ProgressIndicatorUtils.awaitWithCheckCanceled(future)
  }


  private fun buildTargetCommandLine(blackFormattingRequest: BlackFormattingRequest,
                                     targetEnvRequest: TargetEnvironmentRequest): TargetedCommandLine {
    val cmd = TargetedCommandLineBuilder(targetEnvRequest)
    val cmdArgs = configToCmdArguments(blackFormattingRequest)
    val cwd = blackFormattingRequest.virtualFile.parent.takeIf { it != null && it.isDirectory }
    if (cwd != null) {
      cmd.setWorkingDirectory(cwd.path)
    }

    when (blackConfig.executionMode) {
      BlackFormatterConfiguration.ExecutionMode.BINARY -> {
        val blackExecutable = blackConfig.pathToExecutable
        if (blackExecutable == null) {
          throw FileNotFoundException(PyBundle.message("black.empty.path.to.executable.exception.text"))
        }
        cmd.setExePath(blackExecutable)
      }
      BlackFormatterConfiguration.ExecutionMode.PACKAGE -> {
        sdk.configureBuilderToRunPythonOnTarget(cmd)
        cmd.addParameters("-m", BlackFormatterUtil.PACKAGE_NAME)
      }
    }

    cmd.addParameters(cmdArgs)
    cmd.addParameter("-")
    return cmd.build()
  }

  private fun configToCmdArguments(blackFormattingRequest: BlackFormattingRequest): List<String> {
    val cmd = mutableListOf<String>()
    val blackVersion = runBlockingCancellable {
      BlackFormatterVersionService.getVersion (project)
    }

    if (FileTypeRegistry.getInstance().isFileOfType(blackFormattingRequest.virtualFile, PyiFileType.INSTANCE)) {
      cmd.add("--pyi")
    }

    if (blackConfig.cmdArguments.isNotEmpty()) {
      cmd.addAll(blackConfig.cmdArguments.split(" "))
    }

    if (blackVersion >= minimalStdinFilenameCompatibleVersion) {
      cmd.add("--stdin-filename")
      cmd.add(blackFormattingRequest.virtualFile.path)
    }

    if (blackFormattingRequest is BlackFormattingRequest.Fragment) {
      blackFormattingRequest.lineRanges.forEach { range ->
        cmd.add("--line-ranges=${range.first}-${range.last}")
      }
    }

    return cmd
  }

  private fun getFuture(targetCMD: TargetedCommandLine,
                        targetEnvironment: TargetEnvironment,
                        formattingRequest: BlackFormattingRequest,
                        timeout: Duration): CompletableFuture<BlackFormattingResponse> {
    val vFile = formattingRequest.virtualFile

    val future = CompletableFuture<BlackFormattingResponse>()
      .completeOnTimeout(BlackFormattingResponse.Failure(PyBundle.message("black.failed.to.format.on.save.error.label",
                                                                          vFile.name), "Timeout exceeded", null),
                         timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)

    val process = targetEnvironment.createProcess(targetCMD)

    val processHandler = CapturingProcessHandler(process, DEFAULT_CHARSET,
                                                 targetCMD.getCommandPresentation(targetEnvironment))

    processHandler.addProcessListener(writeToStdinListener(formattingRequest.documentText, vFile.charset))

    processHandler.addProcessListener(object : CapturingProcessAdapter() {
      override fun processTerminated(event: ProcessEvent) {
        val exitCode = event.exitCode
        if (exitCode == 0) {
          if (output.stdout.isEmpty()) {
            future.complete(BlackFormattingResponse.Ignored(PyBundle.message("black.file.ignored.notification.label"),
                                                            PyBundle.message("black.file.ignored.notification.message", vFile.name)))
          }
          else {
            future.complete(BlackFormattingResponse.Success(output.stdout))
          }
        }
        else {
          future.complete(BlackFormattingResponse.Failure(
            PyBundle.message("black.failed.to.format.on.save.error.label", vFile.name),
            output.stderr, exitCode))
        }
      }
    })
    processHandler.startNotify()
    return future
  }

  private fun writeToStdinListener(text: String, charset: Charset): ProcessListener {
    return object : ProcessListener {
      override fun startNotified(event: ProcessEvent) {
        try {
          val processInput = event.processHandler.processInput
          if (processInput == null) {
            return
          }
          processInput.write(text.toByteArray(charset))
          processInput.close()
        }
        catch (e: IOException) {
          LOG.error(e)
        }
      }
    }
  }
}