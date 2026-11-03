// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.platform.eel.fs.EelFileUtils
import com.intellij.platform.eel.fs.createTemporaryDirectory
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.getOrThrowFileSystemException
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.HowToReportFile
import com.intellij.python.community.execService.UploadConfig
import com.intellij.python.pixi.backend.PyPixiBackendBundle
import com.intellij.python.sdk.backend.PythonInterpreter
import com.jetbrains.python.errorProcessing.PyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

internal suspend fun resolvePixiSourceName(
  interpreter: PythonInterpreter,
  location: URI,
  indexArguments: List<String> = emptyList(),
): PyResult<String?> = withContext(Dispatchers.IO) {
  val args = Args()
  val workingDirectory: Path?
  val uploadConfig: UploadConfig
  val stages = mutableListOf<Path>()
  try {
    if (location.scheme == "file") {
      val source = Path.of(location)
      val sourceDirectory = if (source.isDirectory()) source else source.parent
      workingDirectory = pixiInterpreterWorkingDirectory(interpreter, sourceDirectory).getOr { return@withContext it }
      val directory = checkNotNull(workingDirectory)
      fun relative(path: Path): String = directory.relativize(path).joinToString("/")
      if (source.isDirectory()) {
        uploadConfig = UploadConfig(source.listDirectoryEntries().filter { it.name != ".pixi" }.map(::relative),
                                    ensureWorkingDirectoryExists = false)
      }
      else {
        uploadConfig = UploadConfig(listOf(relative(source)), ensureWorkingDirectoryExists = false)
      }
      args.addArgs(relative(source).takeIf { it.isNotEmpty() }?.let { "./$it" } ?: ".")
    }
    else {
      workingDirectory = pixiInterpreterWorkingDirectory(interpreter, null).getOr { return@withContext it }
      uploadConfig = UploadConfig(ensureWorkingDirectoryExists = false)
      args.addArgs(location.toASCIIString())
    }
    appendMetadataArguments(args, indexArguments, stages)
    val output = executeBundledPixiPip(interpreter, args, SOURCE_METADATA_PROBE, workingDirectory, uploadConfig)
      .getOr { return@withContext it }
    try {
      PyResult.success(Json.decodeFromString<String>(output.trim()))
    }
    catch (_: SerializationException) {
      sourceMetadataError()
    }
  }
  catch (_: IOException) {
    sourceMetadataError()
  }
  finally {
    withContext(NonCancellable) {
      for (stage in stages) EelFileUtils.deleteRecursively(stage)
    }
  }
}

private suspend fun appendMetadataArguments(args: Args, arguments: List<String>, stages: MutableList<Path>) = withContext(Dispatchers.IO) {
  val iterator = arguments.iterator()
  while (iterator.hasNext()) {
    val argument = iterator.next()
    if (argument != "--find-links" || !iterator.hasNext()) {
      args.addArgs(argument)
      continue
    }
    val location = iterator.next()
    if (!location.startsWith("file:")) {
      args.addArgs(argument, location)
      continue
    }
    val source = Path.of(URI(location))
    val stage = source.getEelDescriptor().toEelApi().fs.createTemporaryDirectory()
      .prefix("pixi-metadata-links-").deleteOnExit(true).getOrThrowFileSystemException().asNioPath()
    stages.add(stage)
    args.addArgs("--pixi-find-links-path")
    if (source.isDirectory()) {
      for (file in source.listDirectoryEntries().filter { it.isRegularFile() }) Files.copy(file, stage.resolve(file.name))
      args.addLocalDir(stage) { reportDir { it to HowToReportFile.AnArgument } }
    }
    else {
      val file = stage.resolve(source.name)
      Files.copy(source, file)
      args.addPath(file)
    }
  }
}

private fun sourceMetadataError(): PyResult<Nothing> =
  PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.location.unsupported"))

private val SOURCE_METADATA_PROBE = """
  import contextlib
  import json
  import os
  from pathlib import Path
  import runpy
  import sys
  import tempfile

  source = sys.argv[1]
  index_arguments = []
  arguments = iter(sys.argv[2:])
  for argument in arguments:
      if argument == '--pixi-find-links-path':
          index_arguments.extend(['--find-links', Path(next(arguments)).absolute().as_uri()])
      else:
          index_arguments.append(argument)
  with tempfile.TemporaryDirectory(prefix='pixi-source-metadata-') as directory:
      report = os.path.join(directory, 'report.json')
      sys.argv = ['pip', 'install', '--dry-run', '--ignore-installed', '--no-deps',
                  '--disable-pip-version-check', '--quiet', '--report', report] + index_arguments + [source]
      with contextlib.redirect_stdout(sys.stderr):
          try:
              runpy.run_module('pip', run_name='__main__', alter_sys=True)
          except SystemExit as result:
              if result.code not in (None, 0):
                  raise
      with open(report, encoding='utf-8') as stream:
          requested = [entry for entry in json.load(stream)['install'] if entry.get('requested')]
      print(json.dumps(requested[0]['metadata']['name'] if len(requested) == 1 else None))
""".trimIndent()
