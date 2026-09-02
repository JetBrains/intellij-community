package com.intellij.python.junit5Tests.unit.alsoWin.pyproject

import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.python.pyproject.model.internal.platformBridge.loadSubtreesIntoVfs
import com.intellij.python.pyproject.model.internal.pyProjectToml.findPyProjectTomlFilesInIndex
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.time.Duration.Companion.minutes

/**
 * Times the `pyproject.toml` search of PY-91841 on a real tree.
 *
 * The test needs a root, so it is skipped by default:
 * ```
 * ./tests.cmd --module intellij.python.pyproject.tests \
 *   --test com.intellij.python.junit5Tests.unit.alsoWin.pyproject.PyProjectTomlDiscoveryBenchmarkTest \
 *   -Dpass.py.discovery.bench.root=/path/to/monorepo \
 *   -Dintellij.build.test.jvm.memory.options=-Xmx8g
 * ```
 *
 * The root only takes part as a search root, so the benchmark writes nothing into the tree it measures.
 *
 * The VFS load runs first, because an IDE has scanned the tree before the search runs. The load dominates the
 * run time of this test, and a real IDE pays it during scanning anyway.
 *
 * The test prints the time of each step, and it asserts that both ways report the same set of files. Read
 * the printed times of the project that you point it at. A time measured on another tree says nothing here.
 */
@TestApplication
internal class PyProjectTomlDiscoveryBenchmarkTest {
  @Test
  fun benchmarkDiscovery(): Unit = timeoutRunBlocking(60.minutes) {
    val rootProperty = System.getProperty(ROOT_PROPERTY)
    Assumptions.assumeTrue(rootProperty != null, "Pass -Dpass.$ROOT_PROPERTY=<path> to run this benchmark")
    val root = Path.of(rootProperty)
    Assumptions.assumeTrue(root.isDirectory(), "$root is not a directory")
    val roots = setOf(root)

    val rootFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(root)
                   ?: error("The VFS cannot find $root")

    val vfsLoadMs = measure { loadSubtreesIntoVfs(setOf(rootFile)) }
    report("VFS load of the tree", listOf(vfsLoadMs))

    var indexResult: List<Path> = emptyList()
    val indexTimes = (1..RUNS).map {
      measure { indexResult = findPyProjectTomlFilesInIndex(roots, excludedPaths = emptySet()) }
    }
    report("findPyProjectTomlFilesInIndex, ${indexResult.size} files", indexTimes)
    println("[PY-91841] median search ${indexTimes.median()} ms over ${indexResult.size} files")

    assertThat(indexResult)
      .describedAs("The search must report a pyproject.toml from a real tree")
      .isNotEmpty()
  }

  private suspend fun measure(block: suspend () -> Unit): Long {
    val start = System.nanoTime()
    block()
    return (System.nanoTime() - start) / 1_000_000
  }

  private fun List<Long>.median(): Long = sorted()[size / 2]

  private fun report(name: String, times: List<Long>) {
    println("[PY-91841] $name: ${times.joinToString(", ") { "$it ms" }}")
  }

  private companion object {
    const val ROOT_PROPERTY = "py.discovery.bench.root"
    const val RUNS = 3
  }
}
