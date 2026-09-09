package com.intellij.python.junit5Tests.unit.alsoWin.pyproject

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.model.internal.platformBridge.loadSubtreesIntoVfs
import com.intellij.python.pyproject.model.internal.pyProjectToml.findPyProjectTomlFilesInIndex
import com.intellij.python.pyproject.model.internal.pyProjectToml.isPrunedName
import com.jetbrains.python.venvReader.VirtualEnvReader
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.isDirectory
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.Duration.Companion.minutes
import kotlin.time.measureTime

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
 * Each test prints the time of every step, and each one compares the old way with the new one. The search
 * test requires the same set of files from both, and the load test requires the same set of environments.
 * Read the printed times of the project that you point it at. A time from another tree says nothing here.
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

    val vfsLoadTime = measure { loadSubtreesIntoVfs(setOf(rootFile)) }
    report("VFS load of the tree", listOf(vfsLoadTime))

    var indexResult: List<Path> = emptyList()
    val indexTimes = (1..RUNS).map {
      measure { indexResult = findPyProjectTomlFilesInIndex(roots, excludedPaths = emptySet()) }
    }
    report("findPyProjectTomlFilesInIndex, ${indexResult.size} files", indexTimes)

    var walkResult: List<Path> = emptyList()
    val walkTime = measure { walkResult = findPyProjectTomlByFilesystemWalk(root) }
    report("the filesystem walk that this change replaced, ${walkResult.size} files", listOf(walkTime))
    println("[PY-91841] search: walk ${walkTime.ms()}, index ${indexTimes.median().ms()}, ${indexResult.size} files")

    assertThat(indexResult)
      .describedAs("The search must report a pyproject.toml from a real tree")
      .isNotEmpty()
    assertThat(indexResult.toSortedSet())
      .describedAs("the index must report the same files as the filesystem walk")
      .isEqualTo(walkResult.toSortedSet())
  }

  /**
   * The search as it worked before this change: one walk of the filesystem for every directory.
   *
   * It stands here only to time the old way and to prove that the new way finds the same files. It mirrors
   * the rules of `PyProjectTomlPathFilter`, so the two results are comparable. It reads [isPrunedName] of
   * the production code rather than a copy, because a copy would drift and this test runs by hand only.
   */
  private fun findPyProjectTomlByFilesystemWalk(root: Path): List<Path> {
    val virtualEnvReader = VirtualEnvReader()
    val hits = ArrayList<Path>()
    Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
      override fun preVisitDirectory(directory: Path, attributes: BasicFileAttributes): FileVisitResult {
        if (directory == root) return FileVisitResult.CONTINUE
        val name = directory.fileName?.toString() ?: return FileVisitResult.SKIP_SUBTREE
        if (name.isPrunedName()) return FileVisitResult.SKIP_SUBTREE
        if (Files.isSymbolicLink(directory)) return FileVisitResult.SKIP_SUBTREE
        if (virtualEnvReader.findPythonInPythonRoot(directory) != null) return FileVisitResult.SKIP_SUBTREE
        return FileVisitResult.CONTINUE
      }

      override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
        if (file.fileName?.toString() == PY_PROJECT_TOML) hits.add(file)
        return FileVisitResult.CONTINUE
      }

      override fun visitFileFailed(file: Path, e: java.io.IOException): FileVisitResult = FileVisitResult.CONTINUE
    })
    return hits
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

    val loadTime = measure { loadSubtreesIntoVfs(setOf(rootFile)) }

    var directories = 0
    val walkTime = measure { walk(rootFile) { directories++ } }

    val virtualEnvReader = VirtualEnvReader()

    // The check that the loader used to run: a filesystem read for every directory.
    val byFilesystem = sortedSetOf<String>()
    val filesystemTime = measure {
      walk(rootFile) { file ->
        val path = file.toNioPathOrNull() ?: return@walk
        if (virtualEnvReader.findPythonInPythonRoot(path) != null) byFilesystem.add(path.toString())
      }
    }

    // The check that the loader runs now: a cheap gate over the loaded names, then the real call.
    val byTwoStage = sortedSetOf<String>()
    var candidates = 0
    val twoStageTime = measure {
      walk(rootFile) { file ->
        val path = file.toNioPathOrNull() ?: return@walk
        if (!virtualEnvReader.mayContainPython(path, file.children.asSequence().map { it.name })) return@walk
        candidates++
        if (virtualEnvReader.findPythonInPythonRoot(path) != null) byTwoStage.add(path.toString())
      }
    }

    println("[PY-91841] loadSubtreesIntoVfs      ${loadTime.ms()} over $directories directories")
    println("[PY-91841] walk only                ${walkTime.ms()} (${perDirectory(walkTime, directories)} us per directory)")
    println("[PY-91841] walk + filesystem check  ${filesystemTime.ms()} (${perDirectory(filesystemTime, directories)} us), found ${byFilesystem.size}")
    println("[PY-91841] walk + two stage check   ${twoStageTime.ms()} (${perDirectory(twoStageTime, directories)} us), found ${byTwoStage.size}, $candidates candidates")
    println("[PY-91841] the check cost, before   ${(filesystemTime - walkTime).ms()}, after ${(twoStageTime - walkTime).ms()}")

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
        if (file.name.isPrunedName()) return false
        onDirectory(file)
        return true
      }
    })
  }

  /** Microseconds per directory, which is the unit that makes a per-directory cost readable. */
  private fun perDirectory(time: Duration, directories: Int): Long =
    if (directories == 0) 0 else time.inWholeMicroseconds / directories

  private suspend fun measure(block: suspend () -> Unit): Duration = measureTime { block() }

  private fun List<Duration>.median(): Duration = sorted()[size / 2]

  private fun report(name: String, times: List<Duration>) {
    println("[PY-91841] $name: ${times.joinToString(", ") { it.ms() }}")
  }

  /** Whole milliseconds. The default text of a [Duration] carries nanoseconds, which no reader of a run needs. */
  private fun Duration.ms(): String = toString(DurationUnit.MILLISECONDS)

  private companion object {
    const val ROOT_PROPERTY = "py.discovery.bench.root"
    const val RUNS = 3
  }
}
