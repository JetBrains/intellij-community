package com.intellij.settingsSync

import com.intellij.openapi.util.NlsSafe
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

/**
 * Synchronizes data with the remote server: pushes the data there, and receives updates.
 * Handles only the "transport" level, i.e. doesn't handle errors, doesn't handle the "push rejected" situation, etc. – all these situations
 * should be processes above.
 */
internal interface SettingsSyncRemoteCommunicator {

  @RequiresBackgroundThread
  fun isUpdateNeeded() : Boolean

  @RequiresBackgroundThread
  fun receiveUpdates(): UpdateResult

  @RequiresBackgroundThread
  fun push(snapshot: SettingsSnapshot): SettingsSyncPushResult

}

internal sealed class UpdateResult {
  class Success(val settingsSnapshot: SettingsSnapshot) : UpdateResult()
  object NoFileOnServer: UpdateResult()
  class Error(@NlsSafe val message: String): UpdateResult()
}
