package com.intellij.ide.starter.testContextCache

import com.intellij.ide.starter.ide.IDERemDevTestContext
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.ide.setFrontendEventLogsMetadataCustomPath
import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.path.FrontendIDEDataPaths
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.project.NoProject
import com.intellij.ide.starter.testContextCache.utils.ProjectFilesRestorer
import com.intellij.ide.starter.utils.createInMemoryDirectory
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.io.copyRecursively
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.relativeTo

/**
 * One entry of [TestContextCache]: the test context of a prepared project.
 *
 * Every current test context created from [testContext] shares its `system` directory, so the indexes of one launch
 * serve the next.
 *
 * The entry captures the prepared state of the project files when it is created, after the setup and before any IDE
 * start. Every way of reuse rewinds the project files to it before its first IDE start, so a test of one way never
 * starts on the files that a test of the other way left.
 */
class CachedTestContext internal constructor(val testContext: IDETestContext) {
  /**
   * The project files right after the setup. It also tracks the build roots, because no IDE has built anything yet.
   * It lives as long as the process, so a later snapshot of the project can share its unchanged files.
   */
  @ApiStatus.Internal
  val preparedProjectFiles: ProjectFilesRestorer? =
    if (testContext.testCase.projectInfo == NoProject) {
      null
    }
    else {
      ProjectFilesRestorer(testContext.resolvedProjectHome, excludedDirectories = emptySet()).apply { captureSnapshot() }
    }

  /**
   * Whether a restore wrote to the project files while no IDE has refreshed its VFS for them yet.
   *
   * A restore writes with plain file-system calls, so the IDE does not see it. The restore runs before the test
   * starts an IDE, so the refresh waits for that IDE, and this flag carries the need across.
   */
  private val projectFilesVfsRefreshPending = AtomicBoolean(false)

  /**
   * Creates a current test context over this entry, in the directory [contextDirName].
   *
   * [testContext] stays the baseline location, for example `out/ide-tests/cache/IU-LOCAL/hello-world/config`.
   * The returned context uses the test home of the test class, for example
   * `out/ide-tests/tests/IU-LOCAL/SearchEverywhereUiTest/config`. It gets a copy of `config`, `plugins`, and the VM
   * options of [testContext], and it shares the `system` directory of [testContext].
   */
  fun createCurrentTestContext(contextDirName: String): IDETestContext {
    val cachedContext = testContext
    val currentTestContext = when (cachedContext) {
      is IDERemDevTestContext -> {
        val backendContext = cachedContext.copyIntoCurrentTestContext(contextDirName)
        val frontendContext = cachedContext.frontendIDEContext.copyIntoCurrentTestContext(contextDirName)
        IDERemDevTestContext.from(backendContext, frontendContext)
          .setFrontendEventLogsMetadataCustomPath(backendContext.paths.eventLogMetadataDir)
      }

      else -> cachedContext.copyIntoCurrentTestContext(contextDirName)
    }
    currentTestContext.overrideOwnDirectories()
    return currentTestContext
  }

  /**
   * Whether [testContext] was created from this entry.
   *
   * A current test context shares the `system` directory of its cached context, and every cached context has its own.
   * A split context has a second one for the frontend, and a split run holds the frontend context.
   */
  internal fun isSourceOf(testContext: IDETestContext): Boolean {
    val cachedSystemDirs = listOfNotNull(this.testContext, (this.testContext as? IDERemDevTestContext)?.frontendIDEContext)
      .map { it.paths.systemDir }
    return testContext.paths.systemDir in cachedSystemDirs
  }

  /**
   * Rewinds the project files to the prepared state. No IDE may run on them during this call.
   *
   * @return whether the restore wrote to the project files. The next IDE then owes a VFS refresh.
   */
  @ApiStatus.Internal
  fun restorePreparedProjectFiles(): Boolean = preparedProjectFiles?.restore() == true

  /** Tells the next IDE over this entry to refresh its VFS for the project files, see [consumeProjectFilesVfsRefresh]. */
  internal fun requestProjectFilesVfsRefresh() {
    projectFilesVfsRefreshPending.set(true)
  }

  internal fun consumeProjectFilesVfsRefresh(): Boolean = projectFilesVfsRefreshPending.getAndSet(false)
}

/** Creates the current test context of one IDE of this cached context: the backend, the frontend, or the monolith. */
@OptIn(ExperimentalPathApi::class)
private fun IDETestContext.copyIntoCurrentTestContext(contextDirName: String): IDETestContext {
  val newContext = IDETestContext(
    paths = createCurrentTestPaths(contextDirName),
    ide = InstalledIdeFromCache(ide, ide.vmOptions.copy()),
    testCase = testCase,
    testName = contextDirName,
    _resolvedProjectHome = _resolvedProjectHome,
    profilerType = profilerType,
    publishers = publishers,
    isReportPublishingEnabled = isReportPublishingEnabled,
    preserveSystemDir = preserveSystemDir,
  )
  // the system directory is shared, so the context owns only its config and its plugins
  listOf(
    paths.configDir to newContext.paths.configDir,
    paths.pluginsDir to newContext.paths.pluginsDir
  ).forEach {
    it.second.deleteRecursively()
    it.first.copyRecursively(it.second)
  }
  logOutput(
    "Copied the config and the plugins of the cached context at ${paths.testHome} " +
    "to the current test context at ${newContext.paths.testHome}"
  )
  return newContext
}

/**
 * The paths of a current test context, over a test home the contexts of one test class share.
 *
 * Built directly rather than through [IDEDataPaths.createPaths], which clears the test home it is given. The home
 * holds the reporting output of every launch of the class, so the paths must not clear it.
 * [copyIntoCurrentTestContext] replaces `config` and `plugins` from the cached context.
 */
private fun IDETestContext.createCurrentTestPaths(contextDirName: String): IDEDataPaths {
  val currentTestHome = currentTestHome(contextDirName)
  // The IDE gets the system directory of the cached context itself, so the VFS caches, the indexes and the project
  // state serve every current test context. IDE processes against one cached project run one at a time.
  val sharedSystemDir = if (testCase.useInMemoryFileSystem) null else paths.systemDir
  val inMemoryRoot = if (testCase.useInMemoryFileSystem) {
    createInMemoryDirectory("ide-integration-test-$contextDirName")
  }
  else {
    null
  }
  return when (paths) {
    is FrontendIDEDataPaths -> FrontendIDEDataPaths(currentTestHome, inMemoryRoot, sharedSystemDir)
    else -> IDEDataPaths(currentTestHome, inMemoryRoot, sharedSystemDir)
  }
}

private fun IDETestContext.currentTestHome(contextDirName: String): Path {
  val sourceContextRoot = if (testCase.ideInfo.isFrontend) {
    paths.testHome.parent ?: error("Frontend test home has no context root: ${paths.testHome}")
  }
  else {
    paths.testHome
  }
  val currentContextRoot = currentContextRoot(sourceContextRoot, contextDirName)
  return if (testCase.ideInfo.isFrontend) currentContextRoot.resolve(paths.testHome.fileName) else currentContextRoot
}

private fun currentContextRoot(sourceContextRoot: Path, contextDirName: String): Path {
  val cacheRoot = GlobalPaths.instance.localCacheDirectory
  require(sourceContextRoot.startsWith(cacheRoot)) {
    "Reusable IDE current test context must be created from a cached context under $cacheRoot, got $sourceContextRoot"
  }
  val cacheRelativeContextRoot = sourceContextRoot.relativeTo(cacheRoot)
  val productRoot = cacheRelativeContextRoot.parent ?: Path.of("")
  return GlobalPaths.instance.testsDirectory.resolve(productRoot).resolve(contextDirName)
}

private fun IDETestContext.overrideOwnDirectories() {
  if (this is IDERemDevTestContext) {
    frontendIDEContext.overrideOwnDirectories()
  }
  ide.vmOptions.overrideDirectories(paths)
}

/** The IDE of a current test context created from a cached one. It has its own copy of the VM options. */
internal class InstalledIdeFromCache(
  private val delegate: InstalledIde,
  override val vmOptions: VMOptions,
) : InstalledIde by delegate
