package com.intellij.settingsSync

import com.intellij.openapi.util.NlsSafe
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus

/**
 * Synchronizes data with the remote server: pushes the data there, and receives updates.
 * Handles only the "transport" level, i.e. doesn't handle errors, doesn't handle the "push rejected" situation, etc. – all these situations
 * should be processes above.
 */
@ApiStatus.Internal
interface SettingsSyncRemoteCommunicator {

  @RequiresBackgroundThread
  fun checkServerState() : ServerState

  @RequiresBackgroundThread
  fun receiveUpdates(): UpdateResult

  @RequiresBackgroundThread
  fun push(snapshot: SettingsSnapshot, force: Boolean, expectedServerVersionId: String?): SettingsSyncPushResult

  @RequiresBackgroundThread
  fun createFile(filePath: String, content: String)

  @RequiresBackgroundThread
  fun deleteFile(filePath: String)

  @RequiresBackgroundThread
  fun isFileExists(filePath: String): Boolean
}

sealed class ServerState {
  object UpdateNeeded: ServerState()
  object UpToDate: ServerState()
  object FileNotExists: ServerState()
  class Error(@NlsSafe val message: String): ServerState()
}

sealed class UpdateResult {
  class Success(val settingsSnapshot: SettingsSnapshot, val serverVersionId: String?) : UpdateResult()
  object NoFileOnServer: UpdateResult()
  object FileDeletedFromServer: UpdateResult()
  class Error(@NlsSafe val message: String): UpdateResult()
}
