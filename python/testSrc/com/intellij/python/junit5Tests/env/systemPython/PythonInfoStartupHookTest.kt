// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.systemPython

import com.intellij.python.community.execService.asBinToExec
import com.intellij.python.community.execService.python.execGetStdout
import com.intellij.python.community.execService.python.validatePythonAndGetInfo
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.intellij.python.venv.createVenv
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.getOrThrow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes

/**
 * An environment can register a startup hook in a `.pth` file, and such a hook can reach a network. The IDE reads the
 * information of an interpreter with `-S`, so no hook runs. See PY-88315.
 */
@PyEnvTestCase
internal class PythonInfoStartupHookTest {

  @Test
  fun testReadingTheInfoRunsNoStartupHook(@PythonBinaryPath basePython: PythonBinary, @TempDir dir: Path): Unit =
    timeoutRunBlocking(10.minutes) {
      val venvDir = dir.resolve("venv")
      val python = createVenv(basePython, venvDir).getOrThrow()
      val marker = dir.resolve("hook-ran.txt")
      installStartupHook(sitePackagesOf(venvDir), marker)

      // The hook is reached by an ordinary start, so a later absence means `-S` kept it out, not a broken hook
      python.asBinToExec().execGetStdout("pass").getOrThrow()
      assertThat(marker).describedAs("The hook did not run even for an ordinary start, so this test proves nothing").exists()
      marker.deleteExisting()

      // The deprecated entry point on purpose: it is the one that starts the interpreter.
      // `PythonEnvironment.getPythonInfo()` reads `pyvenv.cfg` for a venv and starts nothing, so it shows no hook.
      val pythonInfo = python.asBinToExec().validatePythonAndGetInfo().getOrThrow()

      assertThat(pythonInfo.languageLevel.toPythonVersion()).isNotEmpty()
      assertThat(marker).describedAs("Reading the info of $python ran the startup hook of its environment").doesNotExist()
    }

  /** A `.pth` line that starts with `import` runs when the interpreter imports `site`. */
  private fun installStartupHook(sitePackages: Path, marker: Path) {
    sitePackages.resolve("py88315_hook.pth").writeText("import py88315_hook\n")
    val markerLiteral = marker.pathString.replace("\\", "\\\\")
    sitePackages.resolve("py88315_hook.py").writeText("open(\"$markerLiteral\", \"a\").write(\"ran\\n\")\n")
  }

  private fun sitePackagesOf(venvDir: Path): Path {
    val windows = venvDir.resolve("Lib").resolve("site-packages")
    if (windows.isDirectory()) return windows
    return venvDir.resolve("lib").listDirectoryEntries("python*").single().resolve("site-packages")
  }
}
