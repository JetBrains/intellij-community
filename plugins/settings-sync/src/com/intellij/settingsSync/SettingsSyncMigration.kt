package com.intellij.settingsSync

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
interface SettingsSyncMigration {

  /**
   * Checks if the migration should happen and if there is anything to migrate.
   */
  fun isLocalDataAvailable(appConfigDir: Path) : Boolean

  /**
   * Collects settings from the old storage, or returns null if the migration shouldn't happen or if there is nothing to migrate.
   * This method should return null iff [#isLocalDataAvailable] is false.
   */
  fun getLocalDataIfAvailable(appConfigDir: Path): SettingsSnapshot?

  fun migrateCategoriesSyncStatus(appConfigDir: Path, syncSettings: SettingsSyncSettings)

  /**
   * Returns true if the new Settings Sync should be switched on, if this migration is applied.
   */
  fun shouldEnableNewSync(): Boolean

  /**
   * This code is run after the migration have been applied.
   */
  fun executeAfterApplying() {}
}