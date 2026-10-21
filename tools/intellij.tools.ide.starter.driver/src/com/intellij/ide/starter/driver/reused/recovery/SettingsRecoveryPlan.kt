package com.intellij.ide.starter.driver.reused.recovery

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.openapi.application.PathManager.OPTIONS_DIRECTORY
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

/**
 * Describes how the reused IDE session should recover changed settings after a test.
 *
 * Reloadable config files are represented as file specs accepted by
 * `com.intellij.configurationStore.StoreUtil.reloadChangedSettings`.
 * Any non-config state change, or a known non-reloadable config file, requires a full IDE restart.
 *
 * @property changedFileSpecs the files that the restore rewrites, because the test changed or deleted them.
 * @property deletedFileSpecs the files that the restore deletes, because the test created them.
 */
data class SettingsRecoveryPlan(
  val changedFileSpecs: Set<String> = emptySet(),
  val deletedFileSpecs: Set<String> = emptySet(),
  val requiresRestart: Boolean = false,
)

/** Computes the plan from the changes under these baseline roots, as [testContext] sees them. */
internal fun List<BaselineRoot>.settingsRecoveryPlan(testContext: IDETestContext): SettingsRecoveryPlan {
  val changedFileSpecs = LinkedHashSet<String>()
  val deletedFileSpecs = LinkedHashSet<String>()
  var requiresRestart = false

  forEach { root ->
    val changes = root.changes(testContext)
    if (!changes.hasChanges()) {
      return@forEach
    }

    if (!root.reloadable) {
      requiresRestart = true
      return@forEach
    }

    val changedSpecs = (changes.modifiedFiles + changes.deletedFiles).map { it.toApplicationStoreFileSpec() }
    val deletedSpecs = changes.newFiles.map { it.toApplicationStoreFileSpec() }
    changedFileSpecs.addAll(changedSpecs)
    deletedFileSpecs.addAll(deletedSpecs)
    if ((changedSpecs + deletedSpecs).any { it in nonReloadableSettingsFileSpecs }) {
      requiresRestart = true
    }
  }

  return SettingsRecoveryPlan(
    changedFileSpecs = changedFileSpecs,
    deletedFileSpecs = deletedFileSpecs,
    requiresRestart = requiresRestart,
  )
}

/**
 * The config files whose change requires a restart.
 *
 * `reloadChangedSettings` reloads only the storages of persistent state components. The set of disabled plugins is
 * read only at the start, so a change to it stays unapplied in a live IDE. Any other config file is still treated as
 * reloadable, so a new file of that kind must be added here.
 */
internal val nonReloadableSettingsFileSpecs: Set<String> = setOf(
  "customCssProvider.xml",
  "packageManager.xml",
  "disabled_plugins.txt",
)

/**
 * ApplicationStoreImpl maps `$APP_CONFIG$` to `<config>/options`, so option files are identified
 * by names like `editor.xml` when passed to `com.intellij.configurationStore.StoreUtil.reloadChangedSettings`.
 */
private fun Path.toApplicationStoreFileSpec(): String {
  val configRelativePath = invariantSeparatorsPathString
  return configRelativePath.removePrefix("$OPTIONS_DIRECTORY/")
}
