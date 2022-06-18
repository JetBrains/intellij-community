package com.intellij.settingsSync

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.Processor
import com.intellij.util.io.Compressor
import com.intellij.util.io.Decompressor
import java.nio.file.Path

internal object SettingsSnapshotZipSerializer {

  fun serializeToZip(snapshot: SettingsSnapshot): Path {
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

  fun extractFromZip(zipFile: Path): SettingsSnapshot {
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
}