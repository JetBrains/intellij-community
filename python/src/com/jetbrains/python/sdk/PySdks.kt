// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern
import java.util.stream.Collectors

private val PYTHON_INTERPRETER_NAME_UNIX_PATTERN = Pattern.compile("python\\d(\\.\\d+)")

internal fun Path.tryFindPythonBinaries(): List<Path> =
  runCatching { Files.list(this).filter(Path::looksLikePythonBinary).collect(Collectors.toList()) }.getOrElse { emptyList() }

private fun Path.looksLikePythonBinary(): Boolean =
  // `Files.isExecutable(this)` does not work instead of `Files.isRegularFile(this)` for WSL case
  PYTHON_INTERPRETER_NAME_UNIX_PATTERN.matcher(fileName.toString()).matches() && Files.isRegularFile(this)