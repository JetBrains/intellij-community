// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.installer

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.progress.ProcessCanceledException
import java.io.IOException
import java.nio.file.Path

/**
 * Base Exception for the Software Release Installer.
 */
open class ReleaseInstallerException(cause: Throwable) : Exception(cause)

/**
 * Base Exception for the release prepare logic (the stage before onPrepareComplete callback).
 */
open class PrepareException(cause: Throwable) : ReleaseInstallerException(cause)

class WrongSizePrepareException(path: Path, sizeDiff: Long) : PrepareException(
  IOException("Downloaded $path has incorrect size, difference is $sizeDiff bytes.")
)

class WrongChecksumPrepareException(path: Path, required: String, actual: String) : PrepareException(
  IOException("Checksums for $path does not match. Actual value is $actual, expected $required.")
)

class CancelledPrepareException(cause: ProcessCanceledException) : PrepareException(cause)

/**
 * Base Exception for the release processing logic (the stage after onPrepareComplete callback).
 */
open class ProcessException(val command: GeneralCommandLine, val output: ProcessOutput?, cause: Throwable) : ReleaseInstallerException(
  cause)

class ExecutionProcessException(command: GeneralCommandLine, cause: Exception) : ProcessException(command, null, cause)
class NonZeroExitCodeProcessException(command: GeneralCommandLine, output: ProcessOutput)
  : ProcessException(command, output, IOException("Exit code is non-zero: ${output.exitCode}"))

class TimeoutProcessException(command: GeneralCommandLine, output: ProcessOutput)
  : ProcessException(command, output, IOException("Runtime timeout reached"))

class CancelledProcessException(command: GeneralCommandLine, output: ProcessOutput)
  : ProcessException(command, output, IOException("Installation was canceled"))