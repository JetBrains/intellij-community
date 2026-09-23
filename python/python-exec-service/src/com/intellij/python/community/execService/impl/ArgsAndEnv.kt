// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.python.community.execService.FileReporter
import com.intellij.python.community.execService.HowToReportFile
import com.intellij.python.community.execService.RelativePath
import com.jetbrains.python.venvReader.Directory
import java.nio.file.Path

/**
 * [commandArgs] are the process arguments (for example, `--foo=path_to_file`) and [env] is the environment.
 */
@ConsistentCopyVisibility
internal data class ArgsAndEnv private constructor(val commandArgs: List<String>, val env: Map<String, String>) {
  companion object {
    suspend fun create(args: List<Arg>, fileMapper: Uploader): ArgsAndEnv {
      val commandArgs = mutableListOf<String>()
      val env = mutableMapOf<String, String>()
      for (arg in args) {
        when (arg) {
          is Arg.FileArg -> {
            addFileArgument(arg.fileReporter, fileMapper.uploadFile(arg.file), commandArgs, env)
          }
          is Arg.StringArg -> {
            commandArgs.add(arg.arg)
          }
          is Arg.DirArg -> {
            // Upload the full root directory
            val mapper = fileMapper.uploadDir(arg.root)
            for ((filePath, fileReporter) in arg.filesToReport) {
              addFileArgument(fileReporter, mapper.getRemotePath(filePath), commandArgs, env)
            }
          }
        }
      }
      return ArgsAndEnv(commandArgs, env)
    }

    private fun addFileArgument(
      fileReporter: FileReporter,
      fileOnRemoteMachine: FullPathOnTarget,
      commandArgs: MutableList<String>,
      env: MutableMap<String, String>,
    ) {
      val (value, howToReport) = fileReporter.howToReportFile(fileOnRemoteMachine)
      when (howToReport) {
        HowToReportFile.AnArgument -> {
          commandArgs.add(value)
        }
        is HowToReportFile.EnvVar -> {
          env[howToReport.varName] = value
        }
      }
    }
  }
}

internal interface Uploader {
  /**
   * Upload [localFile] and return its [FullPathOnTarget].
   */
  suspend fun uploadFile(localFile: Path): FullPathOnTarget

  /**
   * Upload the full [localDir] and return a [PathMapper] for the files in it.
   */
  suspend fun uploadDir(localDir: Directory): PathMapper
}

internal fun interface PathMapper {
  /**
   * Return the [FullPathOnTarget] of [relativePath] in the uploaded directory.
   */
  fun getRemotePath(relativePath: RelativePath): FullPathOnTarget
}