package com.intellij.ml.local.util

import com.google.gson.Gson
import com.intellij.lang.Language
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.util.io.*
import java.nio.file.Path
import kotlin.io.path.writeText

object StorageUtil {
  private val GSON by lazy { Gson() }

  private const val STORAGE_INFO_FILE = "info.json"

  private fun readInfo(storageDirectory: Path): StorageInfo? {
    val infoFile = storageDirectory.resolve(STORAGE_INFO_FILE)
    if (!infoFile.exists()) return null

    return GSON.fromJson(infoFile.readText(), StorageInfo::class.java)
  }

  fun storagePath(project: Project, language: Language): Path = PathManager.getIndexRoot().toPath()
    .resolve("ml.local.models")
    .resolve(project.locationHash)
    .resolve(language.id)

  fun saveInfo(version: Int, isValid: Boolean, storageDirectory: Path) {
    val infoFile = storageDirectory.resolve(STORAGE_INFO_FILE)
    infoFile.writeText(GSON.toJson(StorageInfo(version, isValid)))
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

  private data class StorageInfo(val version: Int, val isValid: Boolean)
}