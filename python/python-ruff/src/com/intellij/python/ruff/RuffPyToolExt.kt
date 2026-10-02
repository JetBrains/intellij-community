// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.utils.sendWholeText
import com.intellij.platform.eel.provider.utils.stderrString
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.sdk.backend.executeToolInteractive
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.pyi.PyiFileType
import com.jetbrains.python.sdk.ModuleOrProject
import java.io.IOException
import java.nio.file.Path

/**
 * Runs the Ruff executable with [command], feeding [input] to stdin, and returns the captured stdout.
 *
 * Every Ruff entry point pipes the (possibly unsaved) in-memory document through Ruff this way: sending the source over
 * stdin lets Ruff resolve the path-based `pyproject.toml` configuration via `--stdin-filename` while it still operates
 * on the editor text rather than on the version on disk. When Ruff finds no configuration that way, [command] gives
 * it the project config, see [ruffStdinCommand].
 */
internal suspend fun RuffPyTool.runOnStdin(
  moduleOrProject: ModuleOrProject,
  command: RuffStdinCommand,
  input: String,
  execOptions: ExecOptions = ExecOptions(),
): PyResult<String> =
  moduleOrProject.executeToolInteractive(this, command.args, command.workingDir, execOptions) { stdin, processResult ->
    try {
      stdin.sendWholeText(input)
      stdin.close(null)
    }
    catch (ex: IOException) {
      return@executeToolInteractive Result.failure(ex.localizedMessage)
    }

    val output = processResult.await()
    // On exit 0 Ruff always echoes the source it read, so stdout is the result even when it is empty. It is empty for
    // an empty file, and for a file that held nothing but the imports `F401` just removed. Ruff reports a file it
    // cannot parse as a non-zero exit for `format`, and echoes it unchanged for `check`, so neither blanks a file.
    if (output.exitCode != 0) Result.failure(output.stderrString) else Result.success(output.stdoutString)
  }

/** The arguments of one Ruff run on stdin, and the working directory of the run. `null` keeps the default directory. */
internal class RuffStdinCommand(val arguments: List<String>, val workingDir: Path?) {
  val args: Args get() = Args(*arguments.toTypedArray())
}

/** The project config for a Ruff run on [file] of [project], or `null` when Ruff finds a config itself. See [ruffFallbackConfig]. */
internal fun ruffStdinFallback(file: VirtualFile, project: Project): RuffConfigFile? =
  file.parent?.let { ruffFallbackConfig(it, project) }

/**
 * The Ruff run of [subcommand] on stdin for the file at [path], which must come from [ruffPath].
 *
 * `--force-exclude` makes Ruff apply the project's `exclude` settings to [path]. Without it, Ruff treats a path on the
 * command line as an explicit request, and it changes a file that the project excludes. For an excluded file, Ruff
 * returns the input unchanged.
 *
 * A [fallback] from [ruffStdinFallback] goes to Ruff with `--config`. Ruff resolves the relative paths of a `--config`
 * file against the working directory. So the run starts in the directory of that config, where Ruff resolves the
 * relative paths of a config that it finds itself.
 */
internal fun ruffStdinCommand(path: String, fallback: RuffConfigFile?, vararg subcommand: String): RuffStdinCommand {
  val stdinArgs = listOf("--force-exclude", "--stdin-filename", path, "-")
  val configPath = fallback?.file?.ruffPath()
  val configDir = fallback?.file?.parent?.let { it.fileSystem.getNioPath(it) }
  if (configPath == null || configDir == null) return RuffStdinCommand(subcommand.toList() + stdinArgs, null)
  return RuffStdinCommand(subcommand.toList() + listOf("--config", configPath) + stdinArgs, configDir)
}

/** Whether Ruff checks and formats a file of [fileType]: a Python source file or a `.pyi` stub. */
internal fun isRuffFileType(fileType: FileType): Boolean =
  fileType == PythonFileType.INSTANCE || fileType == PyiFileType.INSTANCE

/**
 * The path of this file as the Ruff process sees it, or `null` when the file has no path on that machine.
 *
 * Ruff runs on the interpreter's machine, which is not the IDE's machine for a WSL, Docker, or SSH interpreter. There
 * [VirtualFile.getPath] holds the IDE-side form, such as `//wsl.localhost/Ubuntu/home/...`, which does not exist inside
 * the target. Every path that reaches a Ruff argument must go through this mapping.
 */
internal fun VirtualFile.ruffPath(): String? =
  try {
    toNioPath().asEelPath().toString()
  }
  catch (_: UnsupportedOperationException) {
    null // the file has no nio path: an injected fragment, or a non-local file system
  }
  catch (_: EelPathException) {
    null
  }
  catch (_: NoSuchElementException) {
    null // no Eel root for this path
  }

/** The [ModuleOrProject] that decides which interpreter, and so which Ruff executable, runs for [file]. */
internal fun ruffScopeOf(project: Project, file: VirtualFile): ModuleOrProject {
  val module = ModuleUtilCore.findModuleForFile(file, project)
  return if (module != null) ModuleOrProject.ModuleAndProject(module) else ModuleOrProject.ProjectOnly(project)
}
