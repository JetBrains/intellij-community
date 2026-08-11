// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions
import kotlin.io.path.Path

/**
 * Binary that isn't python. To be used to test validation.
 */
@OptIn(LowLevelLocalMachineAccess::class)
val randomBinary: PythonBinary
  get() = when (OS.CURRENT) {
    OS.Windows -> {
      // ftp.exe is faster than cmd.exe and powershell.exe
      PathEnvironmentVariableUtil.findFirst("ftp.exe") ?: error("No ftp on Windows?")
    }
    else -> Path("/bin/sh")
  }

/**
 * Fails if [this] is not [Result.Failure]
 */
fun Result<*, *>.assertFail() {
  when (this) {
    is Result.Failure -> Assertions.assertNotNull(this.error, "No error")
    is Result.Success -> Assertions.fail("Unexpected success: ${this.result}")
  }
}

/**
 * Close all files opened in [project]. Might be useful to call in `finally` or in [org.junit.jupiter.api.AfterEach]
 */
suspend fun closeAllFiles(project: Project): Unit = withContext(Dispatchers.EDT) {
  writeIntentReadAction {
    FileEditorManager.getInstance(project).apply {
      for (openFile in openFiles) {
        closeFile(openFile)
      }
    }
  }
  EditorHistoryManager.getInstance(project).removeAllFiles()
}
