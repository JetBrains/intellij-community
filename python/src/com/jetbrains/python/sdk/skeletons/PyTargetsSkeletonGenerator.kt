// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.skeletons

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.execution.target.value.getRelativeTargetPath
import com.intellij.execution.target.value.getTargetDownloadPath
import com.intellij.execution.target.value.getTargetUploadPath
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.pathSeparator
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.utils.stderrString
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.BinOnTarget
import com.intellij.python.community.execService.BinaryToExec
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.HowToReportFile.AnArgument
import com.intellij.python.community.execService.ProcessEvent
import com.intellij.python.community.execService.PyProcessListener
import com.intellij.python.community.execService.RelativePath
import com.intellij.python.community.execService.impl.processLaunchers.uploadMeasureTime
import com.intellij.python.community.execService.python.advancedApi.ExecutablePython
import com.intellij.python.community.execService.python.advancedApi.executeHelperAdvanced
import com.intellij.python.sdk.backend.pythonInterpreterAsync
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ExecError
import com.jetbrains.python.errorProcessing.ExecErrorReason
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.run.buildTargetedCommandLine
import com.jetbrains.python.run.prepareHelperScriptExecution
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest
import com.jetbrains.python.sdk.InvalidSdkException
import com.jetbrains.python.sdk.PythonEnvUtil
import com.jetbrains.python.sdk.asBinToExecute
import com.jetbrains.python.sdk.eelNativeMode
import com.jetbrains.python.sdk.skeleton.PySkeletonHeader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.setPosixFilePermissions
import kotlin.time.Duration

internal class PyTargetsSkeletonGenerator(skeletonPath: Path, pySdk: Sdk, currentFolder: String?, project: Project?) :
  PySkeletonGenerator(skeletonPath, pySdk, currentFolder) {
  private val pyRequest: HelpersAwareTargetEnvironmentRequest = checkNotNull(
    // TODO Get rid of the dependency on the default project
    PythonInterpreterTargetEnvironmentFactory.findPythonTargetInterpreter(mySdk, project ?: ProjectManager.getInstance().defaultProject)
  )

  private companion object Args {
    const val SKELETOR_DIR_ARG = "-d"
    const val EXTRA_SYS_PATHS_ARG = "-s"
    const val STATE_FILE_ARG = "--state-file"
    const val INIT_STATE_FILE_ARG = "--init-state-file"
  }

  private val targetEnvRequest: TargetEnvironmentRequest
    get() = pyRequest.targetEnvironmentRequest

  private val foundBinaries: MutableSet<String> = HashSet()

  private fun isLocalTarget() = targetEnvRequest is LocalTargetEnvironmentRequest

  override fun commandBuilder(): Builder {
    val builder = if (isLocalTarget() && eelNativeMode) {
      EelBuilder(mySdk, mySkeletonsPath)
    }
    else {
      TargetedBuilder(mySdk, mySkeletonsPath)
    }
    if (Registry.`is`("python.skeleton.generator.use.process.pool", false)) {
      LOG.info("Using `--use-worker-process-pool` for skeleton generation")
      builder.extraArgs("--use-worker-process-pool")
    }
    myCurrentFolder?.let { builder.workingDir(it) }
    return builder
  }

  /**
   * Note that [mySdk] and [mySkeletonsPath] cannot be accessed directly in [TargetedBuilder]. In the other case [IllegalAccessError] is
   * thrown by access control according to [JVM specification](https://docs.oracle.com/javase/specs/jvms/se16/html/jvms-5.html#jvms-5.4.4).
   */
  private inner class TargetedBuilder(private val sdk: Sdk, private val skeletonsLocalRootPath: Path) : Builder() {
    @RequiresBackgroundThread(generateAssertion = false)
    override fun runProcessWithLineOutputListener(listener: LineWiseProcessOutputListener): ProcessOutput = doRunProcess(listener)

    @RequiresBackgroundThread(generateAssertion = false)
    private fun doRunProcess(listener: LineWiseProcessOutputListener): ProcessOutput {
      val generatorScriptExecution = prepareHelperScriptExecution(
        helperPackage = PythonHelper.GENERATOR3,
        helpersAwareTargetRequest = pyRequest
      )
      generatorScriptExecution.addParameter(SKELETOR_DIR_ARG)
      val skeletonsDownloadRoot = TargetEnvironment.DownloadRoot(
        localRootPath = skeletonsLocalRootPath,
        targetRootPath = TargetEnvironment.TargetPath.Temporary()
      )
      targetEnvRequest.downloadVolumes += skeletonsDownloadRoot
      generatorScriptExecution.addParameter(skeletonsDownloadRoot.getTargetDownloadPath())
      if (myExtraSysPath.isNotEmpty()) {
        generatorScriptExecution.addParameter(EXTRA_SYS_PATHS_ARG)
        // TODO [targets-api] are these paths come from target or from the local machine?
        val pathSeparatorOnTarget = targetEnvRequest.targetPlatform.platform.pathSeparator
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
      // TODO: Unify code
      val existingStateFile = skeletonsPath / STATE_MARKER_FILE
      if (isLocalTarget()) {
        // The local target maps the `-d` output to this persistent dir, so the state file is passed by
        // its real path (no upload/download volume). Persisting bin mtime lets skeleton_status detect a
        // rebuilt binary (e.g. after `maturin develop`) as OUTDATED for local SDKs too, not only remote.
        if (existingStateFile.exists()) {
          generatorScriptExecution.addParameter(STATE_FILE_ARG)
          generatorScriptExecution.addParameter(existingStateFile.toString())
        }
        else {
          generatorScriptExecution.addParameter(INIT_STATE_FILE_ARG)
        }
      }
      else {
        if (existingStateFile.exists()) {
          val localRootPath = Files.createTempDirectory("generator3")
          if (Files.getFileStore(localRootPath).supportsFileAttributeView("posix")) {
            // The directory needs to be readable to all users in case the helpers are run as another user
            localRootPath.setPosixFilePermissions(PosixFilePermissions.fromString("rwxr-xr-x"))
          }

          val stateFileUploadRoot = TargetEnvironment.UploadRoot(
            localRootPath = localRootPath,
            targetRootPath = TargetEnvironment.TargetPath.Temporary(),
          )
          targetEnvRequest.uploadVolumes += stateFileUploadRoot
          Files.copy(existingStateFile, stateFileUploadRoot.localRootPath / STATE_MARKER_FILE)
          generatorScriptExecution.addParameter(STATE_FILE_ARG)
          generatorScriptExecution.addParameter(stateFileUploadRoot.getTargetUploadPath().getRelativeTargetPath(STATE_MARKER_FILE))
        }
        else {
          generatorScriptExecution.addParameter(INIT_STATE_FILE_ARG)
        }
      }
      generatorScriptExecution.addEnvironmentVariable(PythonEnvUtil.PYTHONDONTWRITEBYTECODE, "1")

      val targetEnvironment = targetEnvRequest.prepareEnvironment(TargetProgressIndicator.EMPTY)
      try {

        // XXX Make it automatic
        targetEnvironment.uploadVolumes.values.forEach { it.uploadMeasureTime(".", TargetProgressIndicator.EMPTY, "skeleton") }

        val targetedCommandLine = generatorScriptExecution.buildTargetedCommandLine(targetEnvironment, sdk, emptyList())
        val process = targetEnvironment.createProcess(targetedCommandLine, EmptyProgressIndicator())
        val commandPresentation = targetedCommandLine.getCommandPresentation(targetEnvironment)
        val capturingProcessHandler = CapturingProcessHandler(process, targetedCommandLine.charset, commandPresentation)
        capturingProcessHandler.addProcessListener(LineWiseProcessOutputListener.Adapter(listener))
        val indicator = ProgressManager.getInstance().progressIndicator
        val result = if (indicator != null) {
          capturingProcessHandler.runProcessWithProgressIndicator(indicator)
        }
        else {
          capturingProcessHandler.runProcess()
        }

        // XXX Make it automatic
        targetEnvironment.downloadVolumes.values.forEach { it.download(".", EmptyProgressIndicator()) }
        return result
      }
      finally {
        targetEnvironment.shutdown()
      }
    }
  }

  /**
   * Runs [PythonHelper.GENERATOR3] with [ExecService].
   * [skeletonsLocalRootPath] is given to the process, and the process output is copied back to it.
   */
  private inner class EelBuilder(private val sdk: Sdk, private val skeletonsLocalRootPath: Path) : Builder() {
    @RequiresBackgroundThread(generateAssertion = false)
    override fun runProcessWithLineOutputListener(listener: LineWiseProcessOutputListener): ProcessOutput = runBlockingMaybeCancellable {
      val binary = sdk.pythonInterpreterAsync().asBinToExecute().getOr { throw InvalidSdkException(it.error.message) }

      val args = Args(SKELETOR_DIR_ARG).addLocalDir(skeletonsLocalRootPath) {
        reportDir(andReport = { it to AnArgument })
        downloadAfterExecution()
      }
      if (myExtraSysPath.isNotEmpty()) {
        args.addArgs(EXTRA_SYS_PATHS_ARG, myExtraSysPath.joinToString(separator = binary.pathSeparator))
      }
      args.addArgs(myExtraArgs)
      if (!myTargetModuleName.isNullOrEmpty()) {
        args.addArgs(myTargetModuleName)
        if (!myTargetModulePath.isNullOrEmpty()) {
          args.addArgs(myTargetModulePath)
        }
      }
      if ((skeletonsLocalRootPath / STATE_MARKER_FILE).exists()) {
        // Args keep their order, so the flag goes before the file. The directory is not uploaded again:
        // on the same eel it is used as is, and on a remote eel or a target the first upload is used.
        args.addArgs(STATE_FILE_ARG).addLocalDir(skeletonsLocalRootPath) {
          findChild(RelativePath(STATE_MARKER_FILE), andReport = { it to AnArgument })
        }
      }
      else {
        args.addArgs(INIT_STATE_FILE_ARG)
      }

      val result = ExecService().executeHelperAdvanced(
        python = ExecutablePython(binary, emptyList(), emptyMap()),
        helper = RelativePath { "generator3" / "__main__.py" },
        args = args,
        // The generation of a big SDK can take a long time. The old implementation also has no timeout.
        options = ExecOptions(env = mapOf(PythonEnvUtil.PYTHONDONTWRITEBYTECODE to "1"), timeout = Duration.INFINITE),
        procListener = PyProcessListener { event ->
          when (event) {
            is ProcessEvent.ProcessOutput -> when (event.stream) {
              ProcessEvent.OutputType.STDOUT -> listener.onStdoutLine(event.line)
              ProcessEvent.OutputType.STDERR -> listener.onStderrLine(event.line)
            }
            is ProcessEvent.ProcessStarted, is ProcessEvent.ProcessEnded -> Unit
          }
        },
        // `runGeneration` checks the exit code.
        processOutputTransformer = { Result.success(it) },
      )
      val output = result.getOr { failure ->
        when (val error = failure.error) {
          is ExecError -> when (error.errorReason) {
            ExecErrorReason.Timeout -> return@runBlockingMaybeCancellable ProcessOutput().apply { setTimeout() }
            is ExecErrorReason.CantStart, is ExecErrorReason.UnexpectedProcessTermination -> throw ExecutionException(error.message)
          }
          is MessageError -> throw ExecutionException(error.message)
        }
      }
      ProcessOutput(output.stdoutString, output.stderrString, output.exitCode, false, false)
    }
  }

  /**
   * The separator of a path list on the machine of this binary.
   */
  private val BinaryToExec.pathSeparator: String
    get() = when (this) {
      is BinOnEel -> path.getEelDescriptor().osFamily.pathSeparator
      is BinOnTarget -> targetEnvRequest.targetPlatform.platform.pathSeparator.toString()
    }

  @RequiresBackgroundThread(generateAssertion = false)
  override fun runGeneration(builder: Builder, indicator: ProgressIndicator?): MutableList<GenerationResult> {
    foundBinaries.clear()
    val results = super.runGeneration(builder, indicator)
    results.asSequence()
      .map { it.moduleOrigin }
      .filter { PySkeletonHeader.BUILTIN_NAME != it }
      .toCollection(foundBinaries)
    return results
  }

  override fun exists(name: String): Boolean {
    if (isLocalTarget()) {
      return FileUtil.exists(name)
    }
    else {
      return name in foundBinaries
    }
  }
}
