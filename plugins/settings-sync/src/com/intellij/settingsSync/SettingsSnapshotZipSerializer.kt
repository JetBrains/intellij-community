package com.intellij.settingsSync

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.*
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

  private val LOG = logger<SettingsSnapshotZipSerializer>()

  fun serializeToZip(snapshot: SettingsSnapshot): Path {
    val file = FileUtil.createTempFile(SETTINGS_SYNC_SNAPSHOT_ZIP, null)
    Compressor.Zip(file)
      .use { zip ->
        zip.addFile("$METAINFO/$INFO", serializeMetaInfo(snapshot.metaInfo))

        for (fileState in snapshot.fileStates) {
          val content = if (fileState is FileState.Modified) fileState.content else DELETED_FILE_MARKER.toByteArray()
          zip.addFile(fileState.file, content)
        }
      }
    return file.toPath()
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
    return SettingsSnapshot(metaInfo, fileStates)
  }

  private fun serializeMetaInfo(metaInfo: SettingsSnapshot.MetaInfo): ByteArray {
    val formattedDate = DateTimeFormatter.ISO_INSTANT.format(metaInfo.dateCreated)
    return ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(MetaInfo(formattedDate, metaInfo.applicationId.toString()))
  }

  private fun parseMetaInfo(path: Path): SettingsSnapshot.MetaInfo {
    try {
      val infoFile = path / INFO
      if (infoFile.exists()) {
        val metaInfo = ObjectMapper().readValue(infoFile.readText(), MetaInfo::class.java)
        val date = DateTimeFormatter.ISO_INSTANT.parse(metaInfo.date, Instant::from)
        return SettingsSnapshot.MetaInfo(date, UUID.fromString(metaInfo.applicationId))
      }
      else {
        LOG.warn("Timestamp file doesn't exist")
      }
    }
    catch (e: Throwable) {
      LOG.error("Couldn't read .metainfo from $SETTINGS_SYNC_SNAPSHOT_ZIP", e)
    }
    return SettingsSnapshot.MetaInfo(Instant.now(), applicationId = null)
  }

  private class MetaInfo() {
    lateinit var date: String
    lateinit var applicationId: String

    constructor(date: String, applicationId: String) : this() {
      this.date = date
      this.applicationId = applicationId
    }
  }
}