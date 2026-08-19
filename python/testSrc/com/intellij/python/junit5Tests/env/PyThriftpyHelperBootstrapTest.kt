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
 * Like generator3 (see [PyGenerator3BootstrapTest]), the Python console and profiler helper entry scripts import the
 * bundled `_shaded_thriftpy`, which the IDE makes importable only by injecting `helpers/third_party/thriftpy` into the
 * child process' `PYTHONPATH`. A wrapper interpreter (e.g. an OSGeo4W/QGIS `.bat`) resets `PYTHONPATH` before it finally
 * calls `python.exe`, so that entry is dropped and the helper failed with `ModuleNotFoundError: No module named
 * '_shaded_thriftpy'`. Each script must therefore add that directory to `sys.path` itself, before those imports run.
 *
 * `python <script>.py` puts the script's own directory on `sys.path[0]`; these tests reproduce that (only the script's
 * own dir is importable) with `PYTHONPATH` cleared and a foreign working directory, then import the module so its
 * module-level bootstrap and transitive `_shaded_thriftpy` import run (the `__main__` guard keeps it from starting a
 * server). It must succeed.
 */
@PyEnvTestCase
class PyThriftpyHelperBootstrapTest {

  @Test
  fun `python console starts without an inherited PYTHONPATH`(
    @PythonBinaryPath python: PythonBinary,
    @TempDir foreignCwd: Path,
  ): Unit = runBlocking { assertHelperImportsWithoutInheritedPythonPath(python, foreignCwd, "pydev", "pydevconsole") }

  @Test
  fun `profiler starts without an inherited PYTHONPATH`(
    @PythonBinaryPath python: PythonBinary,
    @TempDir foreignCwd: Path,
  ): Unit = runBlocking { assertHelperImportsWithoutInheritedPythonPath(python, foreignCwd, "profiler", "run_profiler") }

  @Test
  fun `pstat loader starts without an inherited PYTHONPATH`(
    @PythonBinaryPath python: PythonBinary,
    @TempDir foreignCwd: Path,
  ): Unit = runBlocking { assertHelperImportsWithoutInheritedPythonPath(python, foreignCwd, "profiler", "load_pstat") }

  private suspend fun assertHelperImportsWithoutInheritedPythonPath(
    python: PythonBinary,
    foreignCwd: Path,
    subdir: String,
    module: String,
  ) {
    val scriptDir = PythonHelpersLocator.getCommunityHelpersRoot().resolve(subdir)
    assertTrue(scriptDir.resolve("$module.py").exists()) { "helper not found: ${scriptDir.resolve("$module.py")}" }

    // Only the script's own dir is importable (as `python <script>.py` sets sys.path[0]); PYTHONPATH is cleared, so the
    // bundled _shaded_thriftpy dir is reachable only if the script re-adds it itself. The dir is passed out of band so
    // that clearing PYTHONPATH is what actually reproduces the wrapper condition.
    val code = "import os, sys; sys.path.insert(0, os.environ['PY_HELPER_DIR']); import $module"
    val process = withContext(Dispatchers.IO) {
      ProcessBuilder(python.toString(), "-c", code)
        .directory(foreignCwd.toFile())
        .apply {
          environment().remove("PYTHONPATH")
          environment()["PY_HELPER_DIR"] = scriptDir.toString()
        }
        .start()
    }
    val stdout = process.inputStream.readBytes().decodeToString()
    val stderr = process.errorStream.readBytes().decodeToString()
    val exitCode = withContext(Dispatchers.IO) { process.waitFor() }

    assertFalse(
      stderr.contains("No module named '_shaded_thriftpy'"),
      "$module must add the bundled thriftpy dir to sys.path itself (PY-90847). stderr:\n$stderr",
    )
    assertEquals(0, exitCode, "importing $module should succeed without an inherited PYTHONPATH. stdout:\n$stdout\nstderr:\n$stderr")
  }
}
