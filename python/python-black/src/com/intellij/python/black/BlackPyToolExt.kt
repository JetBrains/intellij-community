// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.black

import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.ModuleUtilCore.findModuleForFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.eel.provider.utils.EelProcessExecutionResultInfo
import com.intellij.platform.eel.provider.utils.sendWholeText
import com.intellij.platform.eel.provider.utils.stderrString
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.pytools.executeInteractiveOn
import com.jetbrains.python.Result
import com.intellij.python.black.PyBlackBundle.message
import com.intellij.python.black.configuration.BlackFormatterConfiguration
import com.jetbrains.python.errorProcessing.ExecError
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.pyi.PyiFileType
import com.jetbrains.python.sdk.ModuleOrProject
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@ApiStatus.Internal
suspend fun BlackPyTool.execute(
  project: Project,
  request: BlackFormattingRequest,
  timeout: Duration = 30.seconds,
): BlackFormattingResponse {
  val vFile = request.virtualFile
  val module = findModuleForFile(vFile, project)
  val moduleOrProject = if (module == null) ModuleOrProject.ProjectOnly(project) else ModuleOrProject.ModuleAndProject(module)

  val blackFormatterConfiguration = BlackFormatterConfiguration.getBlackConfiguration(project)
  val args = Args()

  if (FileTypeRegistry.getInstance().isFileOfType(request.virtualFile, PyiFileType.INSTANCE)) {
    args.addArgs("--pyi")
  }

  blackFormatterConfiguration.cmdArguments.trim()
    .split(" ")
    .mapNotNull { s -> s.takeIf { it.isNotBlank() } }
    .let {
      args.addArgs(it)
    }

  args.addArgs("--stdin-filename")
  args.addArgs(request.virtualFile.path)

  if (request is BlackFormattingRequest.Fragment) {
    request.lineRanges
      .map { range -> "--line-ranges=${range.first}-${range.last}" }
      .let {
        args.addArgs(it)
      }
  }

  // Use stdin mode (instead of `--code`): Black evaluates path-based pyproject.toml options
  // (e.g. force-exclude) only when source comes from a path/stdin.
  args.addArgs("-")

  val res = executeInteractiveOn(
    moduleOrProject = moduleOrProject,
    args = args,
    execOptions = ExecOptions(timeout = timeout)
  ) { stdin, processResult ->
    try {
      stdin.sendWholeText(request.documentText)
      stdin.close(null)
    }
    catch (ex: IOException) {
      return@executeInteractiveOn Result.failure(ex.localizedMessage)
    }

    val output = processResult.await()
    when {
      output.exitCode != 0 -> Result.failure(output.stderrString)
      output.stdoutString.isBlank() || "Nothing to do" in output.stderrString -> {
        // When --stdin-filename matches `force-exclude` from pyproject.toml, Black does NOT emit empty
        // stdout — it falls through to `sys.stdout.write(sys.stdin.read())`, echoing the input back.
        // Stderr in that case contains the "Nothing to do" notice; we use it (along with the legacy
        // blank-stdout check for old Black versions) to drive the Ignored branch.
        Result.success(
          BlackFormattingResponse.Ignored(
            message("black.file.ignored.notification.label"),
            message("black.file.ignored.notification.message", vFile.name)
          )
        )
      }
      else -> Result.success(BlackFormattingResponse.Success(output.stdoutString))
    }
  }

  val response = when (res) {
    is Result.Success -> res.result
    is Result.Failure -> res.error.asBlackFailure(vFile)
  }

  return response
}

private fun PyError.asBlackFailure(vFile: VirtualFile): BlackFormattingResponse.Failure {
  val exitCode = ((this as? ExecError)?.errorReason as? EelProcessExecutionResultInfo)?.exitCode
  return BlackFormattingResponse.Failure(
    message("black.failed.to.format.on.save.error.label", vFile.name),
    this.message, exitCode
  )
}
