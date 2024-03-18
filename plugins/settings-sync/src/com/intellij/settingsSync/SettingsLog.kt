package com.intellij.settingsSync

import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus

/**
 * Records changes in the settings, merges changes made locally and remotely.
 */
@ApiStatus.Internal
interface SettingsLog {
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
  fun initialize()

  /**
   * Records all shareable settings from the config directories in their current state.
   * Happens on the very first settings sync initialization, and between IDE sessions.
   */
  @RequiresBackgroundThread
  fun logExistingSettings()

  /**
   * Records the current local state of the settings.
   */
  @RequiresBackgroundThread
  fun applyIdeState(snapshot: SettingsSnapshot, message: String)

  /**
   * Records the state of the settings received from the server.
   *
   * returns true if merge has happened, false in case of fast-forward
   */
  @RequiresBackgroundThread
  fun applyCloudState(snapshot: SettingsSnapshot, message: String)

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
  fun setMasterPosition(position: Position)

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

  /**
   * Restores the repository's state to match that of a specific commit, identified by its hash.
   *
   * This method emulates the behavior of the Git command `git checkout <hash> -- .`, which resets the state of the
   * current working directory to match the state at the specified commit. In addition to checking out files as they
   * were at the specified commit, this method also removes any files from the working directory that were added
   * to the repository after the specified commit.
   */
  fun restoreStateAt(commitHash: String)

  /**
   * Applies the given state to the master branch of the settings without any merging (as opposed to [advanceMaster] which merges
   * changes coming from different sources).
   *
   * This operation is used, for example, when initially taking all the settings from the server: they should be applied right away to
   * the local state, and no merge should happen, since local settings are not needed at this point and should be overwritten.
   *
   * @return New position of 'master'.
   */
  fun forceWriteToMaster(snapshot: SettingsSnapshot, message: String) : Position
}