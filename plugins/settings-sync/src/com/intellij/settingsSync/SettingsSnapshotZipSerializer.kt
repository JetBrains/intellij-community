package com.intellij.settingsSync

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.io.FileUtil
import com.intellij.settingsSync.notification.NotificationService
import com.intellij.settingsSync.plugins.SettingsSyncPluginsState
import com.intellij.util.io.Compressor
import com.intellij.util.io.Decompressor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.function.Consumer
import java.util.stream.Collectors
import kotlin.io.path.*

object SettingsSnapshotZipSerializer {
  private const val METAINFO = ".metainfo"
  private const val INFO = "info.json"
  const val PLUGINS = "plugins.json"

  private val LOG = logger<SettingsSnapshotZipSerializer>()

  private val json = Json { prettyPrint = true }

  fun serializeToZip(snapshot: SettingsSnapshot): Path {
    val file = FileUtil.createTempFile(SETTINGS_SYNC_SNAPSHOT_ZIP, null)
    serialize(snapshot, Compressor.Zip(file))
    /*
    if (file.length() > ZIP_SIZE_SOFT_LIMIT) {
      NotificationService.getInstance().notifyZipSizeExceed()
    }
    */
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
        zip.addFile(fileState.file, getContentForFileState(fileState))
      }

      for((relativePath, content) in serializeSettingsProviders(snapshot.settingsFromProviders)) {
        zip.addFile("$METAINFO/$relativePath", content.toByteArray())
      }

      for (additionalFile in snapshot.additionalFiles) {
        zip.addFile("$METAINFO/${additionalFile.file}", getContentForFileState(additionalFile))
      }
    }
  }

  internal fun serializeSettingsProviders(settingsFromProviders: Map<String, Any>): Map<String, String> {
    val pathsAndContents = mutableMapOf<String, String>()
    for ((id, state) in settingsFromProviders) {
      val provider = SettingsSyncIdeMediatorImpl.findProviderById(id, state)
      if (provider != null) {
        try {
          val string = provider.serialize(state)
          pathsAndContents["$id/${provider.fileName}"] = string
        }
        catch (e: Exception) {
          LOG.error("Could not serialize provider '$id' with $state", e)
        }
      }
    }
    return pathsAndContents
  }

  private fun getContentForFileState(fileState: FileState): ByteArray {
    return if (fileState is FileState.Modified) fileState.content else DELETED_FILE_MARKER.toByteArray()
  }

  private fun serializePlugins(plugins: SettingsSyncPluginsState): String {
    return json.encodeToString(plugins)
  }

  fun extractFromZip(zipFile: Path): SettingsSnapshot? {
    try {
      val tempDir = FileUtil.createTempDirectory("settings.sync.updates", null).toPath()
      Decompressor.Zip(zipFile).extract(tempDir)
      val metaInfoFolder = tempDir / METAINFO
      val metaInfo = parseMetaInfo(metaInfoFolder)

      val fileStates = Files.walk(tempDir)
        .filter { it.isRegularFile() && !it.startsWith(metaInfoFolder) }
        .map { getFileStateFromFileWithDeletedMarker(it, tempDir) }
        .collect(Collectors.toSet())

      val (settingsFromProviders, filesFromProviders) = deserializeSettingsProviders(metaInfoFolder)

      val additionalFiles = Files.walk(metaInfoFolder)
        .filter { it.isRegularFile() && it.name != INFO && it.name != PLUGINS && !filesFromProviders.contains(it) }
        .map { getFileStateFromFileWithDeletedMarker(it, metaInfoFolder) }
        .collect(Collectors.toSet())

      val plugins = deserializePlugins(metaInfoFolder)
      return SettingsSnapshot(metaInfo, fileStates, plugins, settingsFromProviders, additionalFiles)
    } catch (ex: Exception) {
      LOG.warn("Cannot extract settings snapshot from zipFile", ex)
      return null
    }
  }

  internal fun deserializeSettingsProviders(containingFolder: Path): Pair<Map<String, Any>, Set<Path>> {
    val settingsFromProviders = mutableMapOf<String, Any>()
    val filesFromProviders = mutableSetOf<Path>()
    SettingsProvider.SETTINGS_PROVIDER_EP.forEachExtensionSafe(Consumer {
      val file = containingFolder.resolve(it.id).resolve(it.fileName)
      if (file.exists()) {
        val state = it.deserialize(file.readText())
        settingsFromProviders[it.id] = state
        filesFromProviders.add(file)
      }
    })
    return settingsFromProviders to filesFromProviders
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
      buildNumber = snapshotMetaInfo.appInfo?.buildNumber?.asString() ?:""
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
        val appInfo = SettingsSnapshot.AppInfo(
          UUID.fromString(metaInfo.applicationId),
          BuildNumber.fromString(metaInfo.buildNumber),
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
    var buildNumber: String = ""
    var userName: String = ""
    var hostName: String = ""
    var configFolder: String = ""
    var isDeleted: Boolean = false
  }
}
