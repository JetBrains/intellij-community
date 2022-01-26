package com.intellij.settingsSync

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.Processor
import com.intellij.util.io.Compressor
import com.intellij.util.io.Decompressor
import com.intellij.util.io.createDirectories
import com.intellij.util.io.exists
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

// TODO remove: this is temporary decision for prototyping and debugging purposes only
internal const val SETTINGS_SYNC_LOCAL_SERVER_PATH_PROPERTY = "idea.settings.sync.local.server.path"

internal const val SETTINGS_SYNC_SNAPSHOT = "settings.sync.snapshot"
internal const val SETTINGS_SYNC_SNAPSHOT_ZIP = "$SETTINGS_SYNC_SNAPSHOT.zip"

internal class LocalDirSettingsSyncRemoteCommunicator(private val settingsSyncStorage: Path) : SettingsSyncRemoteCommunicator {
  companion object {
    val LOG = logger<LocalDirSettingsSyncRemoteCommunicator>()
  }

  private val serverDir : Path get() {
    val localServerPath = System.getProperty(SETTINGS_SYNC_LOCAL_SERVER_PATH_PROPERTY)
    if (localServerPath == null) {
      LOG.error("Local server path is undefined, using ")
      return settingsSyncStorage.resolveSibling("settingsSyncServer")
    }
    else {
      return Paths.get(localServerPath).createDirectories()
    }
  }

  private val zipFile get() = serverDir.resolve(SETTINGS_SYNC_SNAPSHOT_ZIP)

  override fun isUpdateNeeded(): Boolean {
    return zipFile.exists()
  }

  override fun receiveUpdates(): UpdateResult {
    return UpdateResult.Success(extractZipFile(zipFile))
  }

  override fun push(snapshot: SettingsSnapshot): SettingsSyncPushResult {
    try {
      val file = prepareTempZipFile(snapshot).toFile()
      Files.move(file.toPath(), zipFile, StandardCopyOption.REPLACE_EXISTING)
      return SettingsSyncPushResult.Success
    }
    catch (e: Throwable) {
      LOG.error(e)
      return SettingsSyncPushResult.Error(e.message!!)
    }
  }
}

internal fun prepareTempZipFile(snapshot: SettingsSnapshot): Path {
  val file = FileUtil.createTempFile(SETTINGS_SYNC_SNAPSHOT_ZIP, null)
  Compressor.Zip(file)
    .use { zip ->
      for (fileState in snapshot.fileStates) {
        val content = if (fileState is FileState.Modified) fileState.content else DELETED_FILE_MARKER.toByteArray()
        zip.addFile(fileState.file, content)
      }
    }
  return file.toPath()
}

internal fun extractZipFile(zipFile: Path): SettingsSnapshot {
  val tempDir = FileUtil.createTempDirectory("settings.sync.updates", null)
  Decompressor.Zip(zipFile).extract(tempDir)
  val fileStates = mutableSetOf<FileState>()
  FileUtil.processFilesRecursively(tempDir, Processor {
    if (it.isFile) {
      fileStates.add(getFileStateFromFileWithDeletedMarker(it.toPath(), tempDir.toPath()))
    }
    true
  })
  return SettingsSnapshot(fileStates)
}
