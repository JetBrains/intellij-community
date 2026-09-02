package com.intellij.python.junit5Tests.unit.alsoWin.pyproject

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.python.pyproject.model.internal.platformBridge.loadSubtreesIntoVfs
import com.intellij.python.pyproject.model.internal.platformBridge.mayContainPython
import com.intellij.python.pyproject.model.internal.pyProjectToml.findPyProjectTomlFilesInIndex
import com.jetbrains.python.venvReader.PRUNED_SCAN_DIRS
import com.jetbrains.python.venvReader.VirtualEnvReader
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

  /**
   * Splits the cost of [loadSubtreesIntoVfs] between the VFS and the check for a virtualenv.
   *
   * The loader calls `VirtualEnvReader.findPythonInPythonRoot` for every directory that it visits, and that
   * call ends in `Files.newDirectoryStream`. The loader therefore enumerates every directory of the project
   * on the filesystem, which is the walk that PY-91841 removed from the search.
   *
   * The three walks below run after the load, so the VFS holds the children already. A walk with no check is
   * then the memory cost, and the difference is the cost of the check.
   */
  @Test
  fun benchmarkSubtreeLoad(): Unit = timeoutRunBlocking(60.minutes) {
    val rootProperty = System.getProperty(ROOT_PROPERTY)
    Assumptions.assumeTrue(rootProperty != null, "Pass -Dpass.$ROOT_PROPERTY=<path> to run this benchmark")
    val root = Path.of(rootProperty)
    Assumptions.assumeTrue(root.isDirectory(), "$root is not a directory")
    val rootFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(root) ?: error("The VFS cannot find $root")

    val loadMs = measure { loadSubtreesIntoVfs(setOf(rootFile)) }

    var directories = 0
    val walkMs = measure { walk(rootFile) { directories++ } }

    val virtualEnvReader = VirtualEnvReader()

    // The check that the loader used to run: a filesystem read for every directory.
    val byFilesystem = sortedSetOf<String>()
    val filesystemMs = measure {
      walk(rootFile) { file ->
        val path = file.toNioPathOrNull() ?: return@walk
        if (virtualEnvReader.findPythonInPythonRoot(path) != null) byFilesystem.add(path.toString())
      }
    }

    // The check that the loader runs now: a cheap gate over the loaded names, then the real call.
    val byTwoStage = sortedSetOf<String>()
    var candidates = 0
    val twoStageMs = measure {
      walk(rootFile) { file ->
        val path = file.toNioPathOrNull() ?: return@walk
        if (!mayContainPython(file.children.asSequence().map { it.name })) return@walk
        candidates++
        if (virtualEnvReader.findPythonInPythonRoot(path) != null) byTwoStage.add(path.toString())
      }
    }

    println("[PY-91841] loadSubtreesIntoVfs      ${loadMs} ms over $directories directories")
    println("[PY-91841] walk only                ${walkMs} ms (${perDirectory(walkMs, directories)} us per directory)")
    println("[PY-91841] walk + filesystem check  ${filesystemMs} ms (${perDirectory(filesystemMs, directories)} us), found ${byFilesystem.size}")
    println("[PY-91841] walk + two stage check   ${twoStageMs} ms (${perDirectory(twoStageMs, directories)} us), found ${byTwoStage.size}, $candidates candidates")
    println("[PY-91841] the check cost, before   ${filesystemMs - walkMs} ms, after ${twoStageMs - walkMs} ms")

    assertThat(directories).describedAs("the walk must visit the project").isGreaterThan(0)
    assertThat(byTwoStage)
      .describedAs("the two stage check must find exactly the environments that the filesystem check finds")
      .isEqualTo(byFilesystem)
  }

  /** Visits every directory below [from], with the prune rules of `loadSubtreesIntoVfs`. */
  private fun walk(from: VirtualFile, onDirectory: (VirtualFile) -> Unit) {
    VfsUtilCore.visitChildrenRecursively(from, object : VirtualFileVisitor<Unit>(NO_FOLLOW_SYMLINKS) {
      override fun visitFile(file: VirtualFile): Boolean {
        if (!file.isDirectory) return true
        if (file.name.startsWith(".") || file.name in PRUNED_SCAN_DIRS) return false
        onDirectory(file)
        return true
      }
    })
  }

  private fun perDirectory(millis: Long, directories: Int): Long =
    if (directories == 0) 0 else millis * 1000 / directories

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
