package com.intellij.ide.starter.driver.reused.recovery

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.project.NoProject
import com.intellij.ide.starter.testContextCache.CachedTestContext
import com.intellij.ide.starter.testContextCache.utils.ProjectFilesRestorer
import com.intellij.tools.ide.util.common.logOutput
import java.util.concurrent.ConcurrentHashMap

/**
 * The baseline that recovery of a `@ReusedRun` session restores before the next test.
 *
 * It holds the state that can be restored while the IDE runs: the config, the plugins, and the project state under
 * `system/projects`. It also holds the opened project files, which are restored before a start and during live recovery.
 * The baseline is captured after the reused project has opened and indexed, so a restore keeps the result of the
 * project import. Before the capture, a start goes from the prepared state of [cachedTestContext].
 *
 * It never captures `system/caches` or `system/index`. Every current test context created from one cached context
 * shares the `system` directory of the cached one (`IDEDataPaths.sharedSystemDir`), so the indexes of one launch serve the next.
 */
class ReusedSessionBaseline internal constructor(private val cachedTestContext: CachedTestContext) : AutoCloseable {
  companion object {
    private val baselines = ConcurrentHashMap<CachedTestContext, ReusedSessionBaseline>()

    /**
     * The baseline of [cachedTestContext]. Every session over one cache entry shares it, so the second test class
     * starts from the baseline that the first one captured.
     */
    fun of(cachedTestContext: CachedTestContext): ReusedSessionBaseline {
      return baselines.computeIfAbsent(cachedTestContext) { ReusedSessionBaseline(it) }
    }
  }

  /** The project files after the open. They exclude the build roots, see [ProjectFilesRestorer.DEFAULT_EXCLUDED_DIRECTORIES]. */
  private val openedProjectFiles: ProjectFilesRestorer? =
    cachedTestContext.testContext.takeIf { it.testCase.projectInfo != NoProject }?.let { ProjectFilesRestorer(it.resolvedProjectHome) }

  private var configAndPluginsRoots: List<BaselineRoot> = emptyList()

  private var projectStateRoot: BaselineRoot? = null

  /** The projects that were open at the capture. Recovery reopens them. */
  var openedProjectPaths: List<String> = emptyList()
    private set

  // The windows of the baseline. It can hold a dialog that no test needs. Recovery closes every other window,
  // except a project frame.
  var visibleWindows: List<VisibleWindowData> = emptyList()
    private set

  val isCaptured: Boolean
    get() = projectStateRoot != null

  /**
   * Rewinds the files before an IDE start. The IDE process does not run yet.
   *
   * Before the capture, it rewinds the project files to the prepared state of the cached context. A test that starts
   * an IDE of its own can share the cached context and leave its files behind. After the capture, it calls [restore].
   *
   * @return whether the restore wrote to the project files. The caller then owes the IDE a VFS refresh.
   */
  fun restoreBeforeStart(testContext: IDETestContext): Boolean {
    if (!isCaptured) {
      logOutput("[ReusedRecovery] Restoring the prepared project files before the first start...")
      return cachedTestContext.restorePreparedProjectFiles()
    }
    return restore(testContext)
  }

  /**
   * Rewinds a reused session to the baseline of [capture].
   *
   * It runs in two cases. In live recovery, the IDE runs and its projects are closed. Before a start from the baseline,
   * the IDE process does not run yet. In both cases the build daemons of the session can run, so the project files
   * restore keeps the build roots, see [ProjectFilesRestorer.DEFAULT_EXCLUDED_DIRECTORIES].
   *
   * @return whether the restore wrote to the project files. The caller then owes the IDE a VFS refresh.
   */
  fun restore(testContext: IDETestContext): Boolean {
    val projectStateRoot = checkNotNull(projectStateRoot) { "The reused session baseline is not captured yet" }
    logOutput("[ReusedRecovery] Restoring the reused session baseline...")
    configAndPluginsRoots.forEach { it.restore(testContext) }
    projectStateRoot.restore(testContext)
    return openedProjectFiles?.restore() == true
  }

  /**
   * Captures the baseline from [testContext] and a project files snapshot. The baseline must not be captured yet.
   *
   * The IDE runs during this call, with its projects open and indexed. Its state must be saved and stable before this
   * call, because the baseline reads the files on disk. The driver recovery does this with `saveAndStabilizeStateForReuse`.
   */
  fun capture(
    testContext: IDETestContext,
    openedProjectPaths: List<String>,
    visibleWindows: List<VisibleWindowData>,
  ) {
    check(!isCaptured) { "The reused session baseline is already captured" }
    logOutput("[ReusedRecovery] Capture the reused session baseline")
    close()
    projectStateRoot = BaselineRoot(PROJECT_STATE_ROOT_SPEC, testContext)
    configAndPluginsRoots = testContext.configAndPluginsRootSpecs().map { spec -> BaselineRoot(spec, testContext) }
    openedProjectFiles?.captureSnapshot(base = cachedTestContext.preparedProjectFiles)

    this.openedProjectPaths = openedProjectPaths.also {
      logOutput("[ReusedRecovery] Found initial projects: ${it.joinToString(", ")}")
    }
    this.visibleWindows = visibleWindows.also {
      logOutput("[ReusedRecovery] Found initial visible windows: ${it.joinToString(", ")}")
    }
  }

  /** Computes the cheapest safe settings recovery strategy after the project has been reopened and saved. */
  fun settingsRecoveryPlan(testContext: IDETestContext): SettingsRecoveryPlan {
    check(isCaptured) { "The reused session baseline is not captured yet" }
    return configAndPluginsRoots.settingsRecoveryPlan(testContext)
  }

  override fun close() {
    projectStateRoot?.close()
    projectStateRoot = null
    configAndPluginsRoots.forEach { it.close() }
    configAndPluginsRoots = emptyList()
    openedProjectFiles?.close()
  }
}

/** One visible window of the baseline. */
data class VisibleWindowData(
  val className: String,
  val accessibleName: String?,
)
