// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.impl.processLaunchers.LaunchRequest
import com.intellij.python.community.execService.impl.processLaunchers.createProcessLauncherOnEel
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.deleteExisting
import kotlin.io.path.div
import kotlin.io.path.pathString

/**
 * PY-92079: the work directory can disappear while the process runs.
 * The caller reads the process information outside of any error handling, so this read must not touch the file system.
 */
@TestApplication
internal class ProcessLauncherInfoTest {

  @Test
  fun infoReportsRemovedWorkDir(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val workDir = (tempDir / "workDir").createDirectory()
    val launcher = createProcessLauncherOnEel(
      BinOnEel(path = tempDir / "tool", workDir = workDir),
      LaunchRequest(scopeToBind = this, args = Args(), env = emptyMap(), usePty = null),
    )
    workDir.deleteExisting()
    assertEquals(workDir.pathString, launcher.processCommands.info.cwd)
  }
}
