// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.Result
import com.jetbrains.python.sdk.evolution.deleteEnvDir
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.writeText

/**
 * Guards the refusal in `deleteEnvDir`, which is the last thing standing between a rebuild and an arbitrary directory.
 *
 * The path it deletes descends from one the frontend echoed back, so "is this really an environment" has to be decided
 * here rather than trusted. Every case below is one a wrong answer would delete.
 */
@TestApplication
class PyEvoDeleteEnvDirTest {
  /** A directory shaped like a virtualenv: the interpreter where the reader looks for it, plus its `pyvenv.cfg`. */
  private fun venvAt(root: Path): Path {
    (root / "bin").createDirectories()
    (root / "bin" / "python").createFile()
    (root / "pyvenv.cfg").writeText("home = /usr/bin\n")
    return root
  }

  @Test
  fun `an environment is deleted`(@TempDir tmp: Path) = runTest {
    val env = venvAt(tmp / "env")
    assertInstanceOf(Result.Success::class.java, deleteEnvDir(env))
    assertFalse(env.exists(), "the environment directory should be gone")
  }

  @Test
  fun `a directory holding no interpreter is refused and left standing`(@TempDir tmp: Path) = runTest {
    // The case that matters: a broken round-trip naming, say, the project root must not empty it.
    val notAnEnv = (tmp / "src").createDirectories()
    (notAnEnv / "main.py").createFile()

    assertInstanceOf(Result.Failure::class.java, deleteEnvDir(notAnEnv))
    assertTrue(notAnEnv.exists(), "a directory that is not an environment must survive")
    assertTrue((notAnEnv / "main.py").exists(), "and so must everything in it")
  }

  @Test
  fun `a file is refused`(@TempDir tmp: Path) = runTest {
    val file = (tmp / "pyvenv.cfg").createFile()
    assertInstanceOf(Result.Failure::class.java, deleteEnvDir(file))
    assertTrue(file.exists())
  }

  @Test
  fun `a directory that is not there is refused rather than reported as deleted`(@TempDir tmp: Path) = runTest {
    assertInstanceOf(Result.Failure::class.java, deleteEnvDir(tmp / "gone"))
  }

  /**
   * An environment the OS will not let go of is reported as in use, not as a bare failure (PY-92488).
   *
   * The real case is Windows, where a running interpreter's own `python.exe` cannot be deleted while the process
   * lives. That cannot be staged here, so the same refusal is produced the one way POSIX offers: a directory the
   * walker may not write to. What this pins is the classification — that an `AccessDeniedException` reaches the user
   * as its own message — and the message is what tells them there is something to stop.
   */
  @Test
  @DisabledOnOs(OS.WINDOWS, disabledReason = "Staged via POSIX directory permissions, which Windows does not honour")
  fun `an environment that cannot be deleted is reported as in use`(@TempDir tmp: Path) = runTest {
    val env = venvAt(tmp / "env")
    val sealed = (env / "bin")
    val original = Files.getPosixFilePermissions(sealed)
    // Read and execute, so the walk still enters the directory and finds the interpreter — it is the unlink of the
    // entry inside that is refused, which is the shape of the Windows failure.
    Files.setPosixFilePermissions(sealed, PosixFilePermissions.fromString("r-xr-xr-x"))
    try {
      val result = deleteEnvDir(env)
      assertInstanceOf(Result.Failure::class.java, result)
      val message = (result as Result.Failure).error.message
      assertTrue(message.contains("in use"), "expected the environment to be reported as in use, got: $message")
      assertTrue(env.exists(), "a refused delete must leave the environment standing")
    }
    finally {
      Files.setPosixFilePermissions(sealed, original)
    }
  }
}
