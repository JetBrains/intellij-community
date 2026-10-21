package com.intellij.ide.starter.driver.reused.recovery

import com.intellij.ide.starter.ide.IDERemDevTestContext
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.testContextCache.utils.FileTreeSnapshot
import java.nio.file.Path

/** The baseline of one root, captured from [baselineContext]. */
internal class BaselineRoot(
  private val spec: BaselineRootSpec,
  baselineContext: IDETestContext,
) : AutoCloseable {
  private val snapshot = FileTreeSnapshot.capture(
    root = spec.root(baselineContext),
    shouldTrackDirectory = spec.shouldTrackDirectory,
    shouldTrackFile = spec.shouldTrackFile,
  )
  val reloadable: Boolean
    get() = spec.reloadable

  /**
   * Rewinds this root to its baseline, and touches nothing at all when it is already at it.
   *
   * Live recovery restores while the IDE runs, and [FileTreeSnapshot.restoreAll] deletes a root first. For the
   * `plugins` root, that deletes the jars that the plugin classloaders hold open, and drops an untracked symlink.
   * [settingsRecoveryPlan] turns a change under a non-reloadable root into a restart, so the live path
   * only reaches an unchanged `plugins` root. The verdict comes from the same [FileTreeSnapshot.changes] as the plan.
   */
  fun restore(testContext: IDETestContext) {
    val root = spec.root(testContext)
    val changes = snapshot.changes(root)
    if (!changes.hasChanges()) {
      return
    }
    if (spec.preserveUntrackedPaths) {
      snapshot.restoreTracked(root, changes)
    }
    else {
      snapshot.restoreAll(root)
    }
  }

  fun changes(testContext: IDETestContext): FileTreeSnapshot.FileTreeChanges {
    return snapshot.changes(spec.root(testContext))
  }

  override fun close() {
    snapshot.close()
  }
}

internal class BaselineRootSpec(
  val reloadable: Boolean,
  val preserveUntrackedPaths: Boolean = false,
  val shouldTrackDirectory: (Path) -> Boolean = { true },
  val shouldTrackFile: (Path) -> Boolean = { true },
  private val rootSelector: (IDETestContext) -> Path,
) {
  fun root(testContext: IDETestContext): Path = rootSelector(testContext)
}

internal val PROJECT_STATE_ROOT_SPEC = BaselineRootSpec(reloadable = false) { it.paths.systemDir.resolve("projects") }

/** The `plugins` and `config` roots of this context, and of its frontend in the split mode. */
internal fun IDETestContext.configAndPluginsRootSpecs(): List<BaselineRootSpec> = buildList {
  addConfigAndPluginsRootSpecs { it }
  if (this@configAndPluginsRootSpecs is IDERemDevTestContext) {
    addConfigAndPluginsRootSpecs { (it as IDERemDevTestContext).frontendIDEContext }
  }
}

private fun MutableList<BaselineRootSpec>.addConfigAndPluginsRootSpecs(
  testContext: (IDETestContext) -> IDETestContext,
) {
  add(BaselineRootSpec(reloadable = false) { testContext(it).paths.pluginsDir })
  add(BaselineRootSpec(
    reloadable = true,
    preserveUntrackedPaths = true,
    shouldTrackDirectory = { relativePath -> !relativePath.startsWith(EVENT_LOG_METADATA_DIRECTORY) },
    shouldTrackFile = { relativePath -> relativePath.nameCount > 1 || relativePath.toString() !in ideRuntimeConfigFiles },
  ) { testContext(it).paths.configDir })
}

/**
 * Top-level config files that the running IDE owns, which a settings baseline never tracks.
 *
 * Each IDE writes its own lock at its start, and `ConfigImportHelper` skips the same lock and token files on an import.
 * The IDE keeps the databases open, writes them at most every 2 minutes, and reads them once into memory. So their files
 * do not show their state, and a rewrite under the open database can break it. A reused session never rewinds them,
 * with or without a restart, so their changes go into the next test.
 */
private val ideRuntimeConfigFiles: Set<String> = setOf(
  ".lock",
  "port.lock",
  "token",
  "user.web.token",
  "app-internal-state.db",
  "updatedBrokenPlugins.db",
)

private val EVENT_LOG_METADATA_DIRECTORY: Path = Path.of("event-log-metadata")
