// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.util.SystemInfoRt
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import org.junit.jupiter.api.Assertions
import kotlin.io.path.Path

/**
 * Binary that isn't python. To be used to test validation.
 */
val randomBinary: PythonBinary = Path(
  if (SystemInfoRt.isWindows) {
    // ftp.exe is faster than cmd.exe and powershell.exe
    PathEnvironmentVariableUtil.findInPath("ftp.exe")?.path ?: error("No ftp on Windows?")
  }
  else {
    "/bin/sh"
  })

/**
 * Fails if [this] is not [Result.Failure]
 */
fun Result<*, *>.assertFail() {
  when (this) {
    is Result.Failure -> Assertions.assertNotNull(this.error, "No error")
    is Result.Success -> Assertions.fail("Unexpected success: ${this.result}")
  }
}