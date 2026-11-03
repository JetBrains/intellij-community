package com.intellij.ide.starter.driver.reused.recovery

import com.intellij.ide.starter.ide.IDERemDevTestContext
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.testContextCache.utils.FileTreeSnapshot
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

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
    shouldTrackDirectory = { relativePath ->
      !relativePath.startsWith(EVENT_LOG_METADATA_DIRECTORY) && !relativePath.startsWith(EXTENSIONS_DIRECTORY)
    },
    shouldTrackFile = { relativePath -> relativePath.nameCount > 1 || relativePath.toString() !in ideRuntimeConfigFiles },
  ) { testContext(it).paths.configDir })
}

/**
 * Copies the config databases of [cachedContext] into [testContext], also for the frontend. No IDE may run.
 *
 * The baseline captures a running IDE, so only the cached context holds a saved copy.
 */
internal fun rewindConfigDatabases(testContext: IDETestContext, cachedContext: IDETestContext) {
  for (fileName in configDatabaseFiles) {
    val cachedFile = cachedContext.paths.configDir.resolve(fileName)
    val file = testContext.paths.configDir.resolve(fileName)
    if (cachedFile.exists()) cachedFile.copyTo(file, overwrite = true) else file.deleteIfExists()
  }
  if (testContext is IDERemDevTestContext && cachedContext is IDERemDevTestContext) {
    rewindConfigDatabases(testContext.frontendIDEContext, cachedContext.frontendIDEContext)
  }
}

/**
 * The IDE keeps these databases open, writes them at most every 2 minutes, and reads them once into memory.
 * So their files do not show their state, and a rewrite under the open database can break it.
 * Live recovery never rewinds them. A restart rewinds them with [rewindConfigDatabases].
 */
private val configDatabaseFiles: Set<String> = setOf("app-internal-state.db", "updatedBrokenPlugins.db")

/**
 * Top-level config files that the running IDE owns, which a settings baseline never tracks.
 *
 * Each IDE writes its own lock at its start, and `ConfigImportHelper` skips the same lock and token files on an import.
 */
private val ideRuntimeConfigFiles: Set<String> = setOf(".lock", "port.lock", "token", "user.web.token") + configDatabaseFiles

private val EVENT_LOG_METADATA_DIRECTORY: Path = Path.of("event-log-metadata")

/**
 * The plugin resources that `ExtensionsRootType` extracts on the first use.
 *
 * `ResourceVersions` records each extraction in its `$CACHE_FILE$` state. Live recovery keeps these files.
 * When it rolls back that record, the IDE extracts the files again on the next use.
 */
private val EXTENSIONS_DIRECTORY: Path = Path.of("extensions")
