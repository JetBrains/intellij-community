// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.conda

import com.intellij.openapi.util.SystemInfo
import com.intellij.python.community.junit5Tests.framework.conda.CondaEnv
import com.intellij.python.community.junit5Tests.framework.conda.PyEnvTestCaseWithConda
import com.intellij.python.community.junit5Tests.framework.conda.createCondaEnv
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.python.community.impl.conda.CondaPyTool
import com.intellij.python.pytools.backend.getCustomExecutablePath
import com.intellij.python.pytools.backend.setCustomExecutablePath
import com.intellij.python.sdk.backend.activationEnvironment
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

/**
 * Integration regression test for PY-71917: variables exported by a conda `etc/conda/activate.d` hook must
 * reach the environment PyCharm runs scripts in, even though they belong to no built-in whitelist.
 *
 * Unlike [com.intellij.python.junit5Tests.unit.ActivationEnvDeltaTest], which checks the pure diff in isolation,
 * this drives the real pipeline — a freshly created conda env, a real login shell, and
 * the `activationEnvironment` pipeline (`com.intellij.python.sdk.backend.service.ActivatableEnvironmentService`).
 */
@PyEnvTestCaseWithConda
class CondaActivationEnvTest {

  @Test
  fun `activate_d variables reach the activated environment`(@CondaEnv condaEnv: PyCondaEnv, @TempDir tempDir: Path): Unit =
    timeoutRunBlocking(10.minutes) {
      val envDir = tempDir.resolve("py71917_env")
      createCondaEnv(condaEnv, envDir)

      // A package-specific variable that no whitelist knows about — the essence of PY-71917. conda sources the
      // matching activate.d hook per platform: `*.bat` (via cmd) on Windows, `*.sh` (via the POSIX shell) elsewhere.
      withContext(Dispatchers.IO) {
        val activateD = Files.createDirectories(envDir.resolve("etc/conda/activate.d"))
        if (SystemInfo.isWindows) {
          Files.writeString(activateD.resolve("py71917_marker.bat"), "@set \"PY71917_MARKER=hello-conda-71917\"\r\n")
        }
        else {
          Files.writeString(activateD.resolve("py71917_marker.sh"), "export PY71917_MARKER='hello-conda-71917'\n")
        }
      }

      // The env was just created and is not under <base>/envs, so conda-executable resolution can't find it
      // relative to the env; point it at the base conda so activation is deterministic.
      withCondaPinned(condaEnv) {
        val pythonHome = if (SystemInfo.isWindows) envDir.resolve("python.exe") else envDir.resolve("bin/python")
        val env = pythonHome.activationEnvironment().orThrow()

        // Sanity: conda always exports CONDA_PREFIX on activation, so its absence means activation never ran
        // and the assertion below would be vacuous.
        assertTrue(env.containsKey("CONDA_PREFIX"),
                   "conda activation must have run, but CONDA_PREFIX is absent. Keys: ${env.keys}")
        assertEquals("hello-conda-71917", env["PY71917_MARKER"],
                     "A variable set by a conda activate.d hook must survive activation (PY-71917). Keys: ${env.keys}")
      }
    }

  /**
   * Regression test for the conda #11795 need (formerly a separate launch-time PATH patch): conda runs base
   * python under the hood, whose MKL DLLs live in the base install's `Library\bin`. Activating a non-base env does
   * not add that dir, so [com.intellij.python.sdk.backend.PythonEnvironment.Conda]'s activation post-processor appends it.
   *
   * This asserts the base `Library\bin` ends up on the activated `PATH`. Windows-only: the `Library\bin` layout and
   * #11795 are Windows-specific, and activation is captured through cmd's `activate.bat`.
   */
  @Test
  fun `base Library-bin appended to activated conda PATH (conda 11795)`(@CondaEnv condaEnv: PyCondaEnv, @TempDir tempDir: Path): Unit =
    timeoutRunBlocking(10.minutes) {
      Assumptions.assumeTrue(SystemInfo.isWindows, "conda #11795 (base Library\\bin DLLs) is Windows-only")

      val envDir = tempDir.resolve("py91371_env")
      createCondaEnv(condaEnv, envDir)

      // The env is not under <base>/envs, so point conda-executable resolution at the base conda (as the sibling
      // test does) so that Windows activation via activate.bat is deterministic.
      withCondaPinned(condaEnv) {
        val pythonHome = envDir.resolve("python.exe")
        val env = pythonHome.activationEnvironment().orThrow()

        // Sanity: without this, activation never ran and the assertion below would be vacuous.
        assertTrue(env.containsKey("CONDA_PREFIX"),
                   "conda activation must have run, but CONDA_PREFIX is absent. Keys: ${env.keys}")

        val path = env.entries.first { it.key.equals("PATH", ignoreCase = true) }.value
        val pathDirs = path.split(File.pathSeparatorChar).map { Path.of(it) }
        val baseLibraryBin = Path.of(condaEnv.fullCondaPathOnTarget).parent.parent.resolve("Library").resolve("bin")

        // The activation post-processor must append the base install's Library\bin so conda's under-the-hood base
        // python can load its MKL DLLs (PY-57146), even though activating a non-base env does not add it.
        assertTrue(baseLibraryBin in pathDirs,
                   "Base conda '$baseLibraryBin' must be appended to PATH by activation (PY-57146). PATH=$path")
      }
    }

  /** Runs [block] with the conda of [condaEnv] pinned as the conda of its machine, and restores the previous pin. */
  private suspend fun withCondaPinned(condaEnv: PyCondaEnv, block: suspend () -> Unit) {
    val conda = Path.of(condaEnv.fullCondaPathOnTarget)
    val eel = conda.getEelDescriptor()
    val tool = CondaPyTool.getInstance()
    val previous = tool.getCustomExecutablePath(eel)
    tool.setCustomExecutablePath(eel, conda)
    try {
      block()
    }
    finally {
      tool.setCustomExecutablePath(eel, previous)
    }
  }
}
