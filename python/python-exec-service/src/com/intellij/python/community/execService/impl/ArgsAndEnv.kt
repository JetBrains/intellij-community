// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.python.community.execService.EnvVar
import com.intellij.python.community.execService.EnvVarVal
import com.intellij.python.community.execService.FileReporter
import com.intellij.python.community.execService.HowToReportFile
import com.intellij.python.community.execService.resolveAgainst
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.venvReader.Directory
import java.nio.file.Path

/**
 * [commandArgs] are the process arguments (for example, `--foo=path_to_file`) and [env] is the environment.
 */
@ConsistentCopyVisibility
internal data class ArgsAndEnv private constructor(val commandArgs: List<String>, val env: List<EnvVar>) {
  companion object {
    suspend fun create(args: List<Arg>, fileMapper: Uploader): Result<ArgsAndEnv, MessageError> {
      val commandArgs = mutableListOf<String>()
      val env = mutableListOf<EnvVar>()
      for (arg in args) {
        when (arg) {
          is Arg.FileArg -> {
            val fileOnRemoteMachine = fileMapper.uploadFile(arg.file).getOr { return it }
            addFileArgument(arg.fileReporter, fileOnRemoteMachine, commandArgs, env)
          }
          is Arg.StringArg -> {
            commandArgs.add(arg.arg)
          }
          is Arg.DirArg -> {
            // Upload the full root directory
            val mapper = fileMapper.uploadDir(arg.root).getOr { return it }
            for ((entry, fileReporter) in arg.filesToReport) {
              addFileArgument(fileReporter, mapper.getRemotePath(entry), commandArgs, env)
            }
          }
        }
      }
      return Result.success(ArgsAndEnv(commandArgs, env))
    }

    private fun addFileArgument(
      fileReporter: FileReporter,
      fileOnRemoteMachine: FullPathOnTarget,
      commandArgs: MutableList<String>,
      envs: MutableList<EnvVar>,
    ) {
      val (value, howToReport) = fileReporter.howToReportFile(fileOnRemoteMachine)
      when (howToReport) {
        HowToReportFile.AnArgument -> {
          commandArgs.add(value)
        }
        is HowToReportFile.AsEnvVar -> {
          envs += EnvVar(howToReport.varName, EnvVarVal.RemotePath(value))
        }
      }
    }
  }
}

internal interface Uploader {
  /**
   * Upload [localFile] and return its [FullPathOnTarget], or an error if the upload fails.
   */
  suspend fun uploadFile(localFile: Path): Result<FullPathOnTarget, MessageError>

  /**
   * Upload the full [localDir] and return a [PathMapper] for the files in it, or an error if the upload fails.
   */
  suspend fun uploadDir(localDir: Directory): Result<PathMapper, MessageError>
}

internal fun interface PathMapper {
  /**
   * Return the [FullPathOnTarget] of [entry] in the uploaded directory.
   */
  fun getRemotePath(entry: DirEntry): FullPathOnTarget
}

/**
 * Return the local path of [entry] in [root].
 */
internal fun DirEntry.resolveAgainst(root: Directory): Path = when (this) {
  DirEntry.Root -> root
  is DirEntry.Child -> path.resolveAgainst(root)
}