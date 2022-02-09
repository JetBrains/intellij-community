package com.intellij.settingsSync

import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

/**
 * Records changes in the settings, merges changes made locally and remotely.
 */
internal interface SettingsLog {
  interface Position {
    val id: String
  }

  /**
   * Initializes the log, either from scratch (if the settings sync was not enabled for this IDE yet),
   * or from the existing data on disk (if the log was already used in previous IDE sessions).
   * This method must be called before any operation with the log.
   *
   * @return true if the repository was created from scratch;
   * false if the data was already there, and we've just created internal structures.
   */
  @RequiresBackgroundThread
  fun initialize(): Boolean

  /**
   * Records the current local state of the settings.
   */
  @RequiresBackgroundThread
  fun applyIdeState(snapshot: SettingsSnapshot)

  /**
   * Records the state of the settings received from the server.
   *
   * returns true if merge has happened, false in case of fast-forward
   */
  @RequiresBackgroundThread
  fun applyCloudState(snapshot: SettingsSnapshot)

  /**
   * Returns the current state of the settings as it is now from the SettingsLog point of view,
   * i.e. the state after all completed apply and merge operations.
   */
  @RequiresBackgroundThread
  fun collectCurrentSnapshot(): SettingsSnapshot

  fun getIdePosition(): Position
  fun getCloudPosition(): Position
  fun getMasterPosition(): Position

  fun setIdePosition(position: Position)
  fun setCloudPosition(position: Position)

  /**
   * Moves the master branch to the actual position, which is defined as following:
   * * If the ide branch has advanced further, but the cloud branch didn't, then just move the master branch to the position of the 'ide'
   * (fast-forward merge in Git terminology).
   * * Same if the cloud branch has advanced, but the ide stayed intact.
   * * If both ide and cloud branches have advanced (which means that settings changes were applied both in ide and in cloud more or less
   * simultaneously), then this method merges branches and moves the 'master' label to the new merge position.
   *
   * @return New position of 'master'.
   */
  fun advanceMaster(): Position

}