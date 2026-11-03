package com.intellij.ide.starter.driver.reused.recovery

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.utility
import com.intellij.driver.model.RdTarget
import com.intellij.driver.sdk.saveSettings
import com.intellij.tools.ide.util.common.logError

/**
 * The cache store of one settings target.
 *
 * The IDE keeps the `$CACHE_FILE$` state of the application components in `app-internal-state.db`. The IDE holds the
 * database open, so no file baseline shows that state.
 *
 * @property entries each key to its value in Base64.
 * @property components the `$CACHE_FILE$` components that the IDE has loaded.
 */
internal data class CacheStore(val entries: Map<String, String>, val components: List<CacheFileComponent>)

/** The cache store of each settings target. It is `null` for a target whose IDE cannot list it. */
internal typealias CacheStateSnapshot = Map<RdTarget, CacheStore?>

internal fun Driver.captureCacheState(): CacheStateSnapshot = settingsReloadTargets().associateWith { listCacheStore(it) }

/** Compares the cache store of each settings target with [reference], see [compareCacheStores]. */
internal fun Driver.compareCacheState(reference: CacheStateSnapshot): Map<RdTarget, CacheStateChanges> {
  val current = captureCacheState()
  return settingsReloadTargets().associateWith { target -> compareCacheStores(reference[target], current[target]) }
}

/**
 * The `$CACHE_FILE$` state of one settings target that a test changed.
 *
 * @property restartReason why live recovery cannot restore this state, or `null`. Then the IDE restarts.
 * @property changedKeys each changed key to its `$CACHE_FILE$` component. Recovery rolls back these keys.
 * A key with no component belongs to code that tags its data `CacheTag` itself. Such code keeps a copy in memory, so
 * live recovery treats that data as the memory of the services, and keeps it.
 */
internal data class CacheStateChanges(
  val restartReason: String? = null,
  val changedKeys: Map<String, CacheFileComponent?> = emptyMap(),
) {
  val changedComponents: Set<CacheFileComponent>
    get() = changedKeys.values.filterNotNullTo(LinkedHashSet())
}

/** Compares the cache store of one settings target [before] and [after] a test. Each is `null` when the IDE could not list it. */
internal fun compareCacheStores(before: CacheStore?, after: CacheStore?): CacheStateChanges {
  if (before == null || after == null) {
    val moment = if (before == null) "at the IDE start" else "after the test"
    return CacheStateChanges(restartReason = "recovery cannot list the cache store $moment")
  }
  val changedKeys = (before.entries.keys + after.entries.keys).filterTo(sortedSetOf()) { before.entries[it] != after.entries[it] }
  return CacheStateChanges(changedKeys = changedKeys.associateWith { cacheKeyOwner(it, after.components) })
}

/**
 * Writes the [reference] values of the changed component keys back, reloads their components, and saves the settings.
 * A key with no `$CACHE_FILE$` component stays as the test left it.
 *
 * Then it checks each restored key whose component the IDE had loaded at the [reference] time. Such a key held a saved
 * state, so it must have its [reference] value again. A component that loaded later saves the normalized form of the
 * [reference] value, as `GradleJvmSupportMatrix` does, so recovery trusts its reload. A removed key has no value to
 * compare: its component loads the initial state, and its save can write the default form of it, as
 * `TypeScriptExternalDefinitionsRegistry` writes `{}`.
 * A failed rollback throws, as a failed reload of the files does, so recovery restarts the IDE.
 */
internal fun Driver.rollBackCacheState(changes: Map<RdTarget, CacheStateChanges>, reference: CacheStateSnapshot) {
  for ([target, targetChanges] in changes) {
    val owners = targetChanges.changedKeys
    val keys = owners.filterValues { it != null }.keys
    val referenceStore = reference[target]
    if (keys.isEmpty() || referenceStore == null) {
      continue
    }
    val [restoredKeys, removedKeys] = keys.partition { it in referenceStore.entries }
    utility<CacheStoreRemote>(target).restoreCacheStoreEntries(restoredKeys.map { "$it\t${referenceStore.entries[it]}" }, removedKeys)
    val componentNames = targetChanges.changedComponents.map { it.name }
    val notReloadable = utility<CacheFileComponentsRemote>(target).reloadCacheFileComponents(componentNames)
    check(notReloadable.isEmpty()) { "These components of $target cannot reload: $notReloadable" }
    saveSettings(forceSavingAllSettings = true, rdTargets = listOf(target))
    val after = checkNotNull(listCacheStore(target)) { "Recovery cannot list the cache store of $target after the rollback" }
    val notRolledBack = restoredKeys.filter { owners[it] in referenceStore.components && after.entries[it] != referenceStore.entries[it] }
    check(notRolledBack.isEmpty()) { "The rollback did not apply to these keys of $target: $notRolledBack" }
  }
}

private fun Driver.listCacheStore(target: RdTarget): CacheStore? {
  return runCatching {
    val entries = utility<CacheStoreRemote>(target).describeCacheStore().associate { it.substringBefore('\t') to it.substringAfter('\t') }
    val components = utility<CacheFileComponentsRemote>(target).describeCacheFileComponents() ?: error("The IDE cannot list them")
    CacheStore(entries, components.map { CacheFileComponent(pluginId = it.substringBefore('\t'), name = it.substringAfter('\t')) })
  }
    .onFailure { logError($$"[ReusedRecovery] Cannot list the cache store or the $CACHE_FILE$ components of $$target", it) }
    .getOrNull()
}

/** An application component with the `$CACHE_FILE$` storage. Its keys in the cache store start with [keyPrefix]. */
internal data class CacheFileComponent(val pluginId: String, val name: String) {
  val keyPrefix: String get() = "$pluginId.$name"

  override fun toString(): String = "$pluginId/$name"
}

/**
 * The component of [components] that owns [key], or `null`.
 *
 * A key is `<plugin id>.<component>`, or `<plugin id>.<component>.<property>` for a bean state. When the prefixes of two
 * components match, the longer prefix wins.
 */
private fun cacheKeyOwner(key: String, components: List<CacheFileComponent>): CacheFileComponent? {
  return components
    .filter { key == it.keyPrefix || key.startsWith(it.keyPrefix + ".") }
    .maxByOrNull { it.keyPrefix.length }
}

@Remote("com.intellij.platform.settings.local.LocalSettingsControllerKt", plugin = "com.intellij/intellij.platform.settings.local")
internal interface CacheStoreRemote {
  fun describeCacheStore(): List<String>
  fun restoreCacheStoreEntries(entries: List<String>, removedKeys: List<String>)
}

@Remote("com.intellij.configurationStore.CacheFileComponents")
internal interface CacheFileComponentsRemote {
  fun describeCacheFileComponents(): List<String>?
  fun reloadCacheFileComponents(componentNames: List<String>): List<String>
}
