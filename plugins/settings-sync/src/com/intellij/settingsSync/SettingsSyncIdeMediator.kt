package com.intellij.settingsSync

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Connects the Settings Sync engine with the IDE:
 * when a setting is changed in the IDE, sends it to the Settings Sync to log and push;
 * when the settings sync log gets changed, applies the change to the IDE.
 */
@ApiStatus.Internal
interface SettingsSyncIdeMediator {

  fun applyToIde(snapshot: SettingsSnapshot)

  fun activateStreamProvider()

  fun removeStreamProvider()

  fun getInitialSnapshot(appConfigPath: Path): SettingsSnapshot

}