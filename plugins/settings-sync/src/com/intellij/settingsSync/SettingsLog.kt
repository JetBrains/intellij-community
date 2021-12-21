package com.intellij.settingsSync

import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

/**
 * Records changes in the settings, merges changes made locally and remotely.
 */
internal interface SettingsLog {

  /**
   * Records the current local state of the settings.
   */
  @RequiresBackgroundThread
  fun applyLocalState(snapshot: SettingsSnapshot)

  /**
   * Records the state of the settings received from the server.
   *
   * returns true if merge has happened, false in case of fast-forward
   */
  @RequiresBackgroundThread
  fun applyRemoteState(snapshot: SettingsSnapshot): Boolean //todo improve the return value type API

  /**
   * Returns the current state of the settings as it is now from the SettingsLog point of view,
   * i.e. the state after all completed apply and merge operations.
   */
  @RequiresBackgroundThread
  fun collectCurrentSnapshot(): SettingsSnapshot

  /**
   * Tells the SettingsLog that the settings have been pushed successfully, to let the SettingsLog update its state accordingly.
   */
  fun pushedSuccessfully()

}