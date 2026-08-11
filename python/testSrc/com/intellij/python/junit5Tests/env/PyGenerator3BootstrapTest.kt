// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env

import com.intellij.python.community.helpersLocator.PythonHelpersLocator
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.jetbrains.python.PythonBinary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Regression coverage for [PY-90847](https://youtrack.jetbrains.com/issue/PY-90847).
 *
 * Skeleton generation launches `generator3/__main__.py` and makes the `generator3` package importable purely by
 * injecting the helpers root into the child process' `PYTHONPATH`. A wrapper interpreter (e.g. an OSGeo4W/QGIS `.bat`)
 * resets `PYTHONPATH` before it finally calls `python.exe`, so that entry is dropped and the helper failed with
 * `ModuleNotFoundError: No module named 'generator3'`. The script must therefore add the helpers root to `sys.path`
 * itself, before the top-level `generator3` imports run.
 *
 * This reproduces the wrapper condition directly: the script is launched with `PYTHONPATH` cleared and a foreign
 * working directory (so neither the environment nor the cwd makes `generator3` importable). It must still start.
 */
@PyEnvTestCase
class PyGenerator3BootstrapTest {

  @Test
  fun `generator3 starts without an inherited PYTHONPATH`(
    @PythonBinaryPath python: PythonBinary,
    @TempDir foreignCwd: Path,
  ): Unit = runBlocking {
    val mainScript = PythonHelpersLocator.getCommunityHelpersRoot().resolve("generator3").resolve("__main__.py")
    assertTrue(mainScript.exists()) { "generator3 entry point not found at $mainScript" }

    val process = withContext(Dispatchers.IO) {
      ProcessBuilder(python.toString(), mainScript.toString(), "--help")
        .directory(foreignCwd.toFile())
        .apply { environment().remove("PYTHONPATH") } // reproduce a wrapper interpreter that dropped/reset PYTHONPATH
        .start()
    }
    // `--help` output is tiny, so reading sequentially after the imports/argparse run cannot deadlock the process.
    val stdout = process.inputStream.readBytes().decodeToString()
    val stderr = process.errorStream.readBytes().decodeToString()
    val exitCode = withContext(Dispatchers.IO) { process.waitFor() }

    assertFalse(
      stderr.contains("No module named 'generator3'"),
      "generator3 must be importable without an inherited PYTHONPATH (PY-90847). stderr:\n$stderr",
    )
    assertEquals(0, exitCode, "generator3 --help should exit 0. stdout:\n$stdout\nstderr:\n$stderr")
  }
}
