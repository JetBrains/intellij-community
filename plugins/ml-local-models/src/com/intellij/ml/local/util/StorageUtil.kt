package com.intellij.ml.local.util

import com.google.gson.Gson
import com.intellij.lang.Language
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.io.PersistentHashMap
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
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

  fun <K, V> PersistentHashMap<K, V>.getOrLogError(key: K): V? = try {
    this.get(key)
  } catch (e: IOException) {
    LOG.warn(e)
    null
  }

  fun <K> PersistentHashMap<K, *>.isEmpty(): Boolean = this.processKeysWithExistingMapping { false }

  fun <T> getStorage(storageDirectory: Path, version: Int, factory: (Path, Boolean) -> T): T? {
    try {
      val isValid = prepareStorage(storageDirectory, version)
      return factory(storageDirectory, isValid)
    } catch (t: Throwable) {
      try {
        prepareStorage(storageDirectory, version, forceDelete = true)
        return factory(storageDirectory, false)
      } catch (t: Throwable) {
        LOG.error(t)
        return null
      }
    }
  }

  private fun prepareStorage(storageDirectory: Path, version: Int, forceDelete: Boolean = false): Boolean {
    var isValid = false
    if (storageDirectory.exists()) {
      val info = readInfo(storageDirectory)
      if (forceDelete || info == null || !info.isValid || info.version != version) {
        storageDirectory.delete()
      } else {
        isValid = true
      }
    }
    storageDirectory.createDirectories()
    saveInfo(version, isValid, storageDirectory)
    return isValid
  }

  private data class StorageInfo(val version: Int, val isValid: Boolean, val timestamp: Long)
}
