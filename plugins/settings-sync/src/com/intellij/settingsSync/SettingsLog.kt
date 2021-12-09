package com.intellij.settingsSync

import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.eclipse.jgit.lib.ObjectId

/**
 * Records changes in the settings, merges changes made locally and remotely.
 */
internal interface SettingsLog {

  /**
   * Records the current local state of the settings.
   */
  @RequiresBackgroundThread
  fun recordLocalState(snapshot: SettingsSnapshot): ObjectId? // todo don't expose library type to API

  /**
   * Merges the state of the settings received from the server.
   *
   * returns true if merge has happened, false in case of fast-forward
   * todo improve the return value type API
   */
  fun pull(snapshot: SettingsSnapshot): Boolean

  @RequiresBackgroundThread
  fun getCurrentSnapshot(): SettingsSnapshot

  fun pushedSuccessfully()

}