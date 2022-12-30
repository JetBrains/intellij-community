package com.intellij.settingsSync

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.settingsSync.plugins.SettingsSyncPluginsState
import com.intellij.util.io.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.stream.Collectors
import kotlin.io.path.div

internal object SettingsSnapshotZipSerializer {
  private const val METAINFO = ".metainfo"
  private const val INFO = "info.json"
  private const val PLUGINS = "plugins.json"

  private val LOG = logger<SettingsSnapshotZipSerializer>()

  private val json = Json { prettyPrint = true }

  fun serializeToZip(snapshot: SettingsSnapshot): Path {
    val file = FileUtil.createTempFile(SETTINGS_SYNC_SNAPSHOT_ZIP, null)
    serialize(snapshot, Compressor.Zip(file))
    return file.toPath()
  }

  fun serializeToStream(snapshot: SettingsSnapshot, stream: OutputStream) {
    serialize(snapshot, Compressor.Zip(stream))
  }

  private fun serialize(snapshot: SettingsSnapshot, zipCompressor: Compressor.Zip) {
    zipCompressor.use { zip ->
      zip.addFile("$METAINFO/$INFO", serializeMetaInfo(snapshot.metaInfo))
      if (snapshot.plugins != null) {
        zip.addFile("$METAINFO/$PLUGINS", serializePlugins(snapshot.plugins).toByteArray())
      }

      for (fileState in snapshot.fileStates) {
        val content = if (fileState is FileState.Modified) fileState.content else DELETED_FILE_MARKER.toByteArray()
        zip.addFile(fileState.file, content)
      }
    }
  }

  private fun serializePlugins(plugins: SettingsSyncPluginsState): String {
    return json.encodeToString(plugins)
  }

  fun extractFromZip(zipFile: Path): SettingsSnapshot {
    val tempDir = FileUtil.createTempDirectory("settings.sync.updates", null).toPath()
    Decompressor.Zip(zipFile).extract(tempDir)
    val metaInfoFolder = tempDir / METAINFO
    val metaInfo = parseMetaInfo(metaInfoFolder)
    val fileStates = Files.walk(tempDir)
      .filter { it.isFile() && !metaInfoFolder.isAncestor(it) }
      .map { getFileStateFromFileWithDeletedMarker(it, tempDir) }
      .collect(Collectors.toSet())
    val plugins = deserializePlugins(metaInfoFolder)
    return SettingsSnapshot(metaInfo, fileStates, plugins)
  }

  private fun deserializePlugins(metaInfoFolder: Path): SettingsSyncPluginsState {
    val pluginsFile = metaInfoFolder / PLUGINS
    try {
      if (pluginsFile.exists()) {
        return json.decodeFromString(pluginsFile.readText())
      }
    }
    catch (e: Throwable) {
      LOG.error("Failed to read $pluginsFile", e)
    }
    return SettingsSyncPluginsState(emptyMap())
  }

  private fun serializeMetaInfo(snapshotMetaInfo: SettingsSnapshot.MetaInfo): ByteArray {
    val formattedDate = DateTimeFormatter.ISO_INSTANT.format(snapshotMetaInfo.dateCreated)
    val metaInfo = MetaInfo().apply {
      date = formattedDate
      applicationId = snapshotMetaInfo.appInfo?.applicationId.toString()
      userName = snapshotMetaInfo.appInfo?.userName.toString()
      hostName = snapshotMetaInfo.appInfo?.hostName.toString()
      configFolder = snapshotMetaInfo.appInfo?.configFolder.toString()
      isDeleted = snapshotMetaInfo.isDeleted
    }
    return ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(metaInfo)
  }

  private fun parseMetaInfo(path: Path): SettingsSnapshot.MetaInfo {
    try {
      val infoFile = path / INFO
      if (infoFile.exists()) {
        val metaInfo = ObjectMapper()
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .readValue(infoFile.readText(), MetaInfo::class.java)
        val date = DateTimeFormatter.ISO_INSTANT.parse(metaInfo.date, Instant::from)
        val appInfo = SettingsSnapshot.AppInfo(UUID.fromString(metaInfo.applicationId),
                                               metaInfo.userName, metaInfo.hostName, metaInfo.configFolder)
        return SettingsSnapshot.MetaInfo(date, appInfo, metaInfo.isDeleted)
      }
      else {
        LOG.warn("Timestamp file doesn't exist")
      }
    }
    catch (e: Throwable) {
      LOG.error("Couldn't read .metainfo from $SETTINGS_SYNC_SNAPSHOT_ZIP", e)
    }
    return SettingsSnapshot.MetaInfo(Instant.now(), appInfo = null)
  }

  private class MetaInfo {
    lateinit var date: String
    lateinit var applicationId: String
    var userName: String = ""
    var hostName: String = ""
    var configFolder: String = ""
    var isDeleted: Boolean = false
  }
}