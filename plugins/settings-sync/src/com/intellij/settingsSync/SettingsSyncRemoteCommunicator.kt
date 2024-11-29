package com.intellij.settingsSync

import com.intellij.openapi.util.NlsSafe
import com.intellij.settingsSync.auth.SettingsSyncAuthService
import com.intellij.settingsSync.communicator.SettingsSyncUserData
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus

/**
 * Synchronizes data with the remote server: pushes the data there, and receives updates.
 * Handles only the "transport" level, i.e. doesn't handle errors, doesn't handle the "push rejected" situation, etc. – all these situations
 * should be processes above.
 */
const val CROSS_IDE_SYNC_MARKER_FILE = "cross-ide-sync-enabled"
const val SETTINGS_SYNC_SNAPSHOT = "settings.sync.snapshot"
const val SETTINGS_SYNC_SNAPSHOT_ZIP = "$SETTINGS_SYNC_SNAPSHOT.zip"

@ApiStatus.Internal
interface SettingsSyncRemoteCommunicator {

  /**
   * checks the current state of the user's data in the cloud.
   */
  @RequiresBackgroundThread
  fun checkServerState() : ServerState

  /**
   * Receives updates from server. Is typically called after "checkServerState()"
   */
  @RequiresBackgroundThread
  fun receiveUpdates(): UpdateResult

  /**
   * Pushes the settings snapshot to the remote cloud server only if its remote version is "expectedServerVersionId" or
   * "expectedServerVersionId" is null
   *
   */

  @RequiresBackgroundThread
  fun push(snapshot: SettingsSnapshot, force: Boolean, expectedServerVersionId: String?): SettingsSyncPushResult

  /**
   * Creates a file with a certain content under a relative path
   */
  @RequiresBackgroundThread
  fun createFile(filePath: String, content: String)

  /**
   * deletes a file under a relative path
   */
  @RequiresBackgroundThread
  fun deleteFile(filePath: String)

  /**
   * checks if a file under the relative path exists
   */
  @RequiresBackgroundThread
  fun isFileExists(filePath: String): Boolean
}

sealed class ServerState {
  /**
   * Indicates that an update from the server is required
   */
  object UpdateNeeded: ServerState()

  /**
   * Indicates that there are no remote changes to download
   */
  object UpToDate: ServerState()

  /**
   * Remote file doesn't exist. Means that there's no user data on server
   */
  object FileNotExists: ServerState()

  /**
   * An error occurred during the check.
   */
  class Error(@NlsSafe val message: String): ServerState()
}

sealed class UpdateResult {
  /**
   * Remote update has been successful.
   * @param serverVersionId - current version of the file on server
   * @param isCrossIdeSyncEnabled - indicates whether crossIdeSync is enabled
   */
  class Success(val settingsSnapshot: SettingsSnapshot, val serverVersionId: String?, val isCrossIdeSyncEnabled: Boolean) : UpdateResult()
  object NoFileOnServer: UpdateResult()
  object FileDeletedFromServer: UpdateResult()
  class Error(@NlsSafe val message: String): UpdateResult()
}
