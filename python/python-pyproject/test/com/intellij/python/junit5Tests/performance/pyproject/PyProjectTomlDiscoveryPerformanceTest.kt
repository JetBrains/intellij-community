// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.performance.pyproject

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.model.internal.platformBridge.loadSubtreesIntoVfs
import com.intellij.python.pyproject.model.internal.pyProjectToml.findPyProjectTomlFilesInIndex
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.Timeout
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.time.Duration.Companion.minutes

/**
 * Measures discovery in a fixed tree with 200 packages, 8,000 source files, and 401 visible manifests.
 * The tree also contains hidden directories, dependencies, an excluded directory, and a virtual environment.
 *
 * The load benchmark uses a fresh VFS subtree for each iteration. The filesystem cache can remain warm.
 * The search benchmark uses a loaded tree. Setup, result checks, and cleanup run outside the measured operation.
 *
 * Run from the repository root:
 * ```
 * bash tests.cmd --module intellij.python.pyproject.tests \
 *   --test com.intellij.python.junit5Tests.performance.pyproject.PyProjectTomlDiscoveryPerformanceTest
 * ```
 * The class matches the existing `PYTHON_PERFORMANCE_TESTS` group and exports `metrics.performance.json` through [Benchmark].
 */
@StressTestApplication
@PerformanceUnitTest
@Subsystems.Packaging
@Layers.Performance
@Timeout(value = 10, unit = TimeUnit.MINUTES)
internal class PyProjectTomlDiscoveryPerformanceTest {
  private val pathFixture = tempPathFixture()
  private var tree: DiscoveryTree? = null

  @Test
  fun indexedDiscovery(testInfo: TestInfo) {
    val input = createTree(pathFixture.get().resolve("project"))
    tree = input
    timeoutRunBlocking(2.minutes) {
      loadSubtreesIntoVfs(setOf(input.rootFile), input.excludedPaths)
    }
    val results = ArrayList<List<Path>>()

    Benchmark.newBenchmark("pyproject.toml indexed discovery") {
      results.add(timeoutRunBlocking(2.minutes) {
        findPyProjectTomlFilesInIndex(setOf(input.root), input.excludedPaths)
      })
    }
      .warmupIterations(WARMUP_ITERATIONS)
      .attempts(MEASUREMENT_ITERATIONS)
      .start(testInfo.testMethod.orElseThrow(), "")

    assertThat(results).hasSize(WARMUP_ITERATIONS + MEASUREMENT_ITERATIONS)
    for (result in results) {
      assertThat(result).containsExactlyInAnyOrderElementsOf(input.expectedFiles)
    }
  }

  @Test
  fun loadFreshSubtree(testInfo: TestInfo) {
    var iteration = 0
    Benchmark.newBenchmark("pyproject.toml fresh subtree load") {
      val input = checkNotNull(tree)
      timeoutRunBlocking(2.minutes) {
        loadSubtreesIntoVfs(setOf(input.rootFile), input.excludedPaths)
      }
    }
      .setup {
        tree?.let { assertDiscoveredFiles(it) }
        deleteTree()
        val input = createTree(pathFixture.get().resolve("project-${iteration++}"))
        tree = input
        assertThat(ManagingFS.getInstance().areChildrenLoaded(input.rootFile))
          .describedAs("Each iteration must start with an unloaded VFS subtree")
          .isFalse()
      }
      .warmupIterations(WARMUP_ITERATIONS)
      .attempts(MEASUREMENT_ITERATIONS)
      .start(testInfo.testMethod.orElseThrow(), "")

    assertThat(iteration).isEqualTo(WARMUP_ITERATIONS + MEASUREMENT_ITERATIONS)
    assertDiscoveredFiles(checkNotNull(tree))
  }

  @AfterEach
  fun deleteTree(): Unit = timeoutRunBlocking(2.minutes) {
    tree?.let { input -> edtWriteAction { input.rootFile.delete(this@PyProjectTomlDiscoveryPerformanceTest) } }
    tree = null
  }

  private fun assertDiscoveredFiles(input: DiscoveryTree): Unit = timeoutRunBlocking(2.minutes) {
    assertThat(findPyProjectTomlFilesInIndex(setOf(input.root), input.excludedPaths))
      .containsExactlyInAnyOrderElementsOf(input.expectedFiles)
  }

  private fun createTree(root: Path): DiscoveryTree {
    root.createDirectories()
    val expectedFiles = ArrayList<Path>()
    expectedFiles.add(root.resolve(PY_PROJECT_TOML).createFile())
    repeat(200) { packageIndex ->
      val packageRoot = root.resolve("packages/group-${packageIndex / 20}/package-$packageIndex").createDirectories()
      expectedFiles.add(packageRoot.resolve(PY_PROJECT_TOML).createFile())
      repeat(10) { directoryIndex ->
        val directory = packageRoot.resolve("src/part-$directoryIndex").createDirectories()
        repeat(4) { directory.resolve("module$it.py").createFile() }
        if (directoryIndex == 9) {
          expectedFiles.add(directory.resolve(PY_PROJECT_TOML).createFile())
        }
      }
    }

    val excluded = root.resolve("build-output")
    for (name in listOf(".cache", "node_modules", "build-output", "environment")) {
      val ignored = root.resolve(name).createDirectories()
      repeat(100) {
        ignored.resolve("dependency-$it/nested").createDirectories().resolve(PY_PROJECT_TOML).createFile()
      }
    }
    val environment = root.resolve("environment")
    environment.resolve("bin").createDirectories().resolve("python").createFile()
    environment.resolve("Scripts").createDirectories().resolve("python.exe").createFile()

    val rootFile = checkNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(root))
    return DiscoveryTree(root, rootFile, setOf(excluded), expectedFiles)
  }

  private data class DiscoveryTree(
    val root: Path,
    val rootFile: VirtualFile,
    val excludedPaths: Set<Path>,
    val expectedFiles: List<Path>,
  )

  private companion object {
    const val WARMUP_ITERATIONS = 5
    const val MEASUREMENT_ITERATIONS = 5
  }
}
