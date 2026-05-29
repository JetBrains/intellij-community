// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.uv

import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pytools.runtime.PyToolRuntime
import com.intellij.python.uv.backend.runtime.uvCli
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.getOrThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.seconds

@PyEnvTestCase
class UvCliTest {
  private lateinit var myRuntime: PyToolRuntime
  private lateinit var projectRootPath: Path
  private lateinit var projectName: String

  companion object {
    private lateinit var uvContext: UvContext

    @BeforeAll
    @JvmStatic
    fun configureUvRuntime(@PythonBinaryPath pythonPath: PythonBinary, @TempDir tempDir: Path) {
      uvContext = UvContext.create(pythonPath, tempDir)
    }

    private data class PinnedPackage(val name: String, val version: String) {
      /** PEP 508 spec passed to `uv tool install`. */
      fun spec(): String = "$name==$version"
    }

    /**
     * Tiny pure-Python CLI on PyPI (a couple of kilobytes) with a stable release history.
     * Keeps install fast and avoids transitive-dependency churn that could flake the
     * outdated check. cowsay 5.0 was released in 2022; 6.x has been out since 2023, so this
     * is reliably "outdated" in the eyes of `uv tool list --outdated`. Bump only if cowsay
     * yanks 5.0.
     */
    private val CYCLE_TEST_PACKAGE: PinnedPackage = PinnedPackage(name = "cowsay", version = "5.0")
  }

  @BeforeEach
  fun setUp(testInfo: TestInfo, @TempDir tempDir: Path) {
    val realTempDir = tempDir.toRealPath()
    this.projectName = testInfo.projectName()
    this.projectRootPath = realTempDir.resolve(this.projectName)

    val tempDirUvCli = uvContext.globalRuntime.withWorkingDirectory(realTempDir).getOrThrow().uvCli()
    timeoutRunBlocking { tempDirUvCli.init(projectName) }.getOrThrow()

    this.myRuntime = uvContext.globalRuntime.withWorkingDirectory(projectRootPath).getOrThrow()
    assertTrue(projectRootPath.exists())
    assertTrue(projectRootPath.resolve(PY_PROJECT_TOML).exists())
  }

  @Test
  fun testHelp() = timeoutRunBlocking {
    val output = myRuntime.uvCli().help("version").getOrThrow()
    assertTrue(output.isNotBlank())
  }

  @Test
  fun testGetVersion() = timeoutRunBlocking {
    val version = myRuntime.uvCli().getVersion().getOrThrow()
    assertTrue(version.isNotBlank())
  }

  @Test
  fun testSync() = timeoutRunBlocking(60.seconds) {
    myRuntime.uvCli().sync().getOrThrow()
    assertTrue(projectRootPath.resolve(".venv").exists())
  }

  @Test
  fun testAuth() = timeoutRunBlocking {
    val dir = myRuntime.uvCli().auth().dir().getOrThrow()
    assertTrue(dir.isNotBlank())
  }

  @Test
  fun testCache() = timeoutRunBlocking {
    val cache = myRuntime.uvCli().cache()
    assertTrue(cache.dir().getOrThrow().isNotBlank())
    assertTrue(cache.size().getOrThrow().isNotBlank())
  }

  @Test
  fun testPython(): Unit = timeoutRunBlocking(60.seconds) {
    val python = myRuntime.uvCli().python()
    assertTrue(python.dir().getOrThrow().isNotBlank())
    python.list(onlyInstalled = true).getOrThrow()
  }

  @Test
  fun testSelf() = timeoutRunBlocking {
    val version = myRuntime.uvCli().self().version(short = true).getOrThrow()
    assertTrue(version.isNotBlank())
  }

  @Test
  fun testTool(): Unit = timeoutRunBlocking(60.seconds) {
    val tool = myRuntime.uvCli().tool()
    assertTrue(tool.dir().getOrThrow().isNotBlank())
    tool.list().getOrThrow()
  }

  /**
   * Full install→list→upgrade cycle against PyPI. Uses [TOOL_FOR_CYCLE_TEST] pinned to
   * [PINNED_OLD_VERSION], which is intentionally older than the latest release so that
   * `uv tool list --outdated` has something to report. After upgrade the same query must
   * no longer mention the tool. Network-dependent — runs in the env test suite next to
   * the other live-uv tests.
   */
  @Test
  fun testToolInstallListUpgradeCycle(): Unit = timeoutRunBlocking(180.seconds) {
    val tool = myRuntime.uvCli().tool()
    val pkg = CYCLE_TEST_PACKAGE

    // 1. Install a pinned older version.
    tool.install(pkg.spec()).getOrThrow()

    // Sanity-check that [UvContext]'s class-scoped `UV_TOOL_DIR` redirection actually steers
    // `uv` away from its default user-wide location: the freshly installed tool must live
    // under [UvContext.uvToolDirPath]. Without this assertion a misconfigured env would
    // silently pollute the developer's machine and other tests could see the leaked tool.
    val expectedToolEnv = uvContext.uvToolDirPath.resolve(pkg.name)
    assertTrue(expectedToolEnv.exists()) {
      "expected ${pkg.name} install under the class-scoped UV_TOOL_DIR ${uvContext.uvToolDirPath}, missing: $expectedToolEnv"
    }

    // 2. listInstalled() should surface the freshly installed tool at the pinned version.
    val installed = tool.listInstalled().getOrThrow()
    val installedEntry = installed.firstOrNull { it.name == pkg.name }
    assertNotNull(installedEntry) { "expected ${pkg.name} in listInstalled(), got $installed" }
    assertEquals(pkg.version, installedEntry!!.version) {
      "expected ${pkg.spec()} right after install, got ${installedEntry.version}"
    }

    // 3. listOutdated() should report it with a newer latestVersion (we pinned to an older release).
    val outdatedBefore = tool.listOutdated().getOrThrow()
    val outdatedEntry = outdatedBefore.firstOrNull { it.name == pkg.name }
    assertNotNull(outdatedEntry) {
      "expected ${pkg.name} in listOutdated() before upgrade, got $outdatedBefore"
    }
    assertEquals(pkg.version, outdatedEntry!!.currentVersion)
    assertNotEquals(pkg.version, outdatedEntry.latestVersion) {
      "latestVersion must differ from the pinned ${pkg.version} for the outdated signal to mean anything"
    }

    // 4. `uv tool upgrade` is bounded by the original install constraints — pinning to
    //    ==${pkg.version} makes the upgrade a no-op even though a newer release exists.
    //    The call must still succeed, and the outdated state must be unchanged afterwards.
    //    This is exactly the production path that surfaces "{tool} is already up to date" in
    //    the External Tools settings balloon.
    tool.upgrade(pkg.name).getOrThrow()
    val outdatedAfterUpgrade = tool.listOutdated().getOrThrow()
    assertTrue(outdatedAfterUpgrade.any { it.name == pkg.name }) {
      "uv tool upgrade respects the original pin; ${pkg.name} should still be outdated, got $outdatedAfterUpgrade"
    }

    // 5. `install(name, reinstall = true)` (uv's `--reinstall`) drops the prior pin and
    //    installs the latest release. After that the outdated list must no longer mention it.
    tool.install(pkg.name, reinstall = true).getOrThrow()
    val outdatedAfterReinstall = tool.listOutdated().getOrThrow()
    assertTrue(outdatedAfterReinstall.none { it.name == pkg.name }) {
      "after install(reinstall=true) ${pkg.name} should drop off listOutdated(), got $outdatedAfterReinstall"
    }
  }
}
