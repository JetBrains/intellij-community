// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Describes a CLI tool managed by a [PythonPackageManager]: its executable name,
 * how to resolve the binary on disk, and whether it runs as a Python module (`python -m <name>`).
 */
@ApiStatus.Internal
data class PythonManagerCliSpec(
  val executableName: String,
  val resolveExecutable: suspend () -> Path?,
  val runAsModule: Boolean = false,
)
