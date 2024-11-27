package com.intellij.settingsSync

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.delete
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlin.io.path.inputStream

abstract class AbstractServerCommunicator() : SettingsSyncRemoteCommunicator {
  companion object {
    private val LOG = logger<AbstractServerCommunicator>()
  }


  @VisibleForTesting
  @Throws(IOException::class, SecurityException::class)
  protected fun currentSnapshotFilePath(): Pair<String, Boolean>? {
    try {
      val crossIdeSyncEnabled = isFileExists(CROSS_IDE_SYNC_MARKER_FILE)
      if (crossIdeSyncEnabled != SettingsSyncLocalSettings.getInstance().isCrossIdeSyncEnabled) {
        LOG.info("Cross-IDE sync status on server is: ${enabledOrDisabled(crossIdeSyncEnabled)}. Updating local settings with it.")
        SettingsSyncLocalSettings.getInstance().isCrossIdeSyncEnabled = crossIdeSyncEnabled
      }
      if (crossIdeSyncEnabled) {
        return Pair(SETTINGS_SYNC_SNAPSHOT_ZIP, true)
      }
      else {
        return Pair("${ApplicationNamesInfo.getInstance().productName.lowercase()}/$SETTINGS_SYNC_SNAPSHOT_ZIP", false)
      }
    }
    catch (e: Throwable) {
      if (e is IOException || e is SecurityException) {
        throw e
      }
      else {
        LOG.warn("Couldn't check if $CROSS_IDE_SYNC_MARKER_FILE exists", e)
        return null
      }
    }
  }


  @VisibleForTesting
  internal fun sendSnapshotFile(
    inputStream: InputStream,
    knownServerVersion: String?,
    force: Boolean,
  ): SettingsSyncPushResult {
    val snapshotFilePath: String
    val defaultMessage = "Unknown during checking $CROSS_IDE_SYNC_MARKER_FILE"
    try {
      snapshotFilePath = currentSnapshotFilePath()?.first ?: return SettingsSyncPushResult.Error(defaultMessage)
    }
    catch (ioe: IOException) {
      return SettingsSyncPushResult.Error(ioe.message ?: defaultMessage)
    }

    val versionToPush: String?
    if (force) {
      // get the latest server version: pushing with it will overwrite the file in any case
      versionToPush = getLatestVersion(snapshotFilePath)
      writeFileInternal(snapshotFilePath, null, inputStream)
    }
    else {
      if (knownServerVersion != null) {
        versionToPush = knownServerVersion
      }
      else {
        val serverVersion = getLatestVersion(snapshotFilePath)
        if (serverVersion == null) {
          // no file on the server => just push it there
          versionToPush = null
        }
        else {
          // we didn't store the server version locally yet => reject the push to avoid overwriting the server version;
          // the next update after the rejected push will store the version information, and subsequent push will be successful.
          return SettingsSyncPushResult.Rejected
        }
      }
      writeFileInternal(snapshotFilePath, versionToPush, inputStream)
    }

    // errors are thrown as exceptions, and are handled above
    return SettingsSyncPushResult.Success(versionToPush)
  }

  override fun checkServerState(): ServerState {
    try {
      val snapshotFilePath = currentSnapshotFilePath()?.first ?: return ServerState.Error("Unknown error during checkServerState")
      val latestVersion = getLatestVersion(snapshotFilePath)
      LOG.debug("Latest version info: $latestVersion")
      requestSuccessful()
      when (latestVersion) {
        null -> return ServerState.FileNotExists
        SettingsSyncLocalSettings.getInstance().knownAndAppliedServerId -> return ServerState.UpToDate
        else -> return ServerState.UpdateNeeded
      }
    }
    catch (e: Throwable) {
      val message = handleRemoteError(e)
      return ServerState.Error(message)
    }
  }

  override fun receiveUpdates(): UpdateResult {
    LOG.info("Receiving settings snapshot from the cloud config server...")
    try {
      val (snapshotFilePath, isCrossIdeSync) = currentSnapshotFilePath() ?: return UpdateResult.Error("Unknown error during receiveUpdates")
      val (stream, version) = readFileInternal(snapshotFilePath)
      requestSuccessful()
      if (stream == null) {
        LOG.info("$snapshotFilePath not found on the server")
        return UpdateResult.NoFileOnServer
      }

      val tempFile = FileUtil.createTempFile(SETTINGS_SYNC_SNAPSHOT, UUID.randomUUID().toString() + ".zip")
      try {
        FileUtil.writeToFile(tempFile, stream.readAllBytes())
        val snapshot = SettingsSnapshotZipSerializer.extractFromZip(tempFile.toPath())
        if (snapshot == null) {
          LOG.info("cannot extract snapshot from tempFile ${tempFile.toPath()}. Implying there's no snapshot")
          return UpdateResult.NoFileOnServer
        }
        else {
          return if (snapshot.isDeleted()) UpdateResult.FileDeletedFromServer else UpdateResult.Success(snapshot, version, isCrossIdeSync)
        }
      }
      finally {
        FileUtil.delete(tempFile)
      }
    }
    catch (e: Throwable) {
      val message = handleRemoteError(e)
      return UpdateResult.Error(message)
    }
  }

  override fun push(snapshot: SettingsSnapshot, force: Boolean, expectedServerVersionId: String?): SettingsSyncPushResult {
    LOG.info("Pushing setting snapshot to the cloud config server...")
    val zip = try {
      SettingsSnapshotZipSerializer.serializeToZip(snapshot)
    }
    catch (e: Throwable) {
      LOG.warn(e)
      return SettingsSyncPushResult.Error(e.message ?: "Couldn't prepare zip file")
    }

    try {
      val pushResult = sendSnapshotFile(zip.inputStream(), expectedServerVersionId, force)
      requestSuccessful()
      return pushResult
    }
    catch (ive: InvalidVersionIdException) {
      LOG.info("Rejected: version doesn't match the version on server: ${ive.message}")
      return SettingsSyncPushResult.Rejected
    }
    catch (e: Throwable) {
      val message = handleRemoteError(e)
      return SettingsSyncPushResult.Error(message)
    }
    finally {
      try {
        zip.delete()
      }
      catch (e: Throwable) {
        LOG.warn(e)
      }
    }
  }

  protected abstract fun requestSuccessful()

  protected abstract fun handleRemoteError(e: Throwable): String

  @Throws(IOException::class)
  protected abstract fun readFileInternal(snapshotFilePath: String): Pair<InputStream?, String?>

  @Throws(IOException::class, InvalidVersionIdException::class)
  protected abstract fun writeFileInternal(filePath: String, versionId: String?, content: InputStream): String?

  @Throws(IOException::class)
  protected abstract fun getLatestVersion(filePath: String) : String?

  @Throws(IOException::class)
  protected abstract fun deleteFileInternal(filePath: String)

  override fun createFile(filePath: String, content: String) {
    writeFileInternal(filePath, null, content.byteInputStream())
  }

  @Throws(IOException::class)
  override fun deleteFile(filePath: String) {
    SettingsSyncLocalSettings.getInstance().knownAndAppliedServerId = null
    deleteFileInternal(filePath)
  }

  @Throws(IOException::class)
  override fun isFileExists(filePath: String): Boolean {
    return getLatestVersion(filePath) != null
  }
}

class InvalidVersionIdException(override val message: String, override val cause: Throwable? = null) : RuntimeException(message, cause) {}