package com.intellij.settingsSync

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.stream.Collectors
import kotlin.io.path.div

internal object SettingsSnapshotZipSerializer {
  private const val METAINFO = ".metainfo"
  private const val TIMESTAMP = "timestamp"

  private val LOG = logger<SettingsSnapshotZipSerializer>()

  fun serializeToZip(snapshot: SettingsSnapshot): Path {
    val file = FileUtil.createTempFile(SETTINGS_SYNC_SNAPSHOT_ZIP, null)
    Compressor.Zip(file)
      .use { zip ->
        val formattedDate = DateTimeFormatter.ISO_INSTANT.format(snapshot.metaInfo.dateCreated)
        zip.addFile("$METAINFO/$TIMESTAMP", formattedDate.toByteArray())

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

  private fun parseMetaInfo(path: Path): SettingsSnapshot.MetaInfo {
    try {
      val timestampFile = path / TIMESTAMP
      if (timestampFile.exists()) {
        val timestamp = timestampFile.readText()
        val date = DateTimeFormatter.ISO_INSTANT.parse(timestamp, Instant::from)
        return SettingsSnapshot.MetaInfo(date)
      }
      else {
        LOG.warn("Timestamp file doesn't exist")
      }
    }
    catch (e: Throwable) {
      LOG.error("Couldn't read .metainfo from $SETTINGS_SYNC_SNAPSHOT_ZIP", e)
    }
    return SettingsSnapshot.MetaInfo(Instant.now())
  }
}