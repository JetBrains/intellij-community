package com.intellij.ml.local.util

import com.google.gson.Gson
import com.intellij.lang.Language
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.io.*
import java.nio.file.Path
import kotlin.io.path.writeText

object StorageUtil {
  private val LOG = logger<StorageUtil>()
  private val GSON by lazy { Gson() }

  private const val STORAGE_INFO_FILE = "info.json"

  private fun readInfo(storageDirectory: Path): StorageInfo? {
    val infoFile = storageDirectory.resolve(STORAGE_INFO_FILE)
    if (!infoFile.exists()) return null
    return try {
      GSON.fromJson(infoFile.readText(), StorageInfo::class.java)
    } catch (e: Throwable) {
      LOG.error(e)
      null
    }
  }

  fun storagePath(project: Project, language: Language): Path =
    Path.of(PathManager.getSystemPath(), "ml.local.models", project.locationHash, language.id)

  fun saveInfo(version: Int, isValid: Boolean, storageDirectory: Path) {
    val infoFile = storageDirectory.resolve(STORAGE_INFO_FILE)
    infoFile.writeText(GSON.toJson(StorageInfo(version, isValid, System.currentTimeMillis())))
  }

  fun <K> PersistentHashMap<K, *>.isEmpty(): Boolean = this.processKeysWithExistingMapping { false }

  fun<K> PersistentHashMap<K, *>.clear() {
    val existing = HashSet<K>()
    this.processKeysWithExistingMapping {
      existing.add(it)
      true
    }
    existing.forEach { fileId ->
      this.remove(fileId)
    }
  }

  fun prepareStorage(storageDirectory: Path, version: Int) {
    if (storageDirectory.exists()) {
      val info = readInfo(storageDirectory)
      if (info == null || !info.isValid || info.version != version) {
        storageDirectory.delete()
      }
    }
    storageDirectory.createDirectories()
    saveInfo(version, true, storageDirectory)
  }

  private data class StorageInfo(val version: Int, val isValid: Boolean, val timestamp: Long)
}