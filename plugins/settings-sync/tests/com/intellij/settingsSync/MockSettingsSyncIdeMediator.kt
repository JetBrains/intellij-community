package com.intellij.settingsSync

import com.intellij.util.io.isAncestor
import com.intellij.util.io.isFile
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path

internal class MockSettingsSyncIdeMediator : SettingsSyncIdeMediator {
  internal val files = mutableMapOf<String, String>()

  private var exceptionToThrowOnApply: Exception? = null

  override fun applyToIde(snapshot: SettingsSnapshot) {
    if (exceptionToThrowOnApply != null) {
      throw exceptionToThrowOnApply!!
    }

    for (fileState in snapshot.fileStates) {
      if (fileState is FileState.Modified) {
        files[fileState.file] = String(fileState.content, Charset.defaultCharset())
      }
      else {
        files.remove(fileState.file)
      }
    }
  }

  override fun activateStreamProvider() {
  }

  override fun removeStreamProvider() {
  }

  override fun collectFilesToExportFromSettings(appConfigPath: Path): () -> Collection<Path> {
    return getAllFilesFromSettings(appConfigPath)
  }

  fun throwOnApply(exception: Exception) {
    exceptionToThrowOnApply = exception
  }

  companion object {
    fun getAllFilesFromSettings(appConfigPath: Path): () -> Collection<Path> {
      return {
        val settingsSyncStorage = appConfigPath.resolve(SETTINGS_SYNC_STORAGE_FOLDER)
        Files.walk(appConfigPath).filter {
          it.isFile() && !settingsSyncStorage.isAncestor(it)
        }.toList()
      }
    }
  }
}
