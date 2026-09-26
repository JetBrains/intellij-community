package com.intellij.ml.local.models.frequency.classes

import com.intellij.ml.local.models.api.LocalModelStorage
import com.intellij.ml.local.util.StorageUtil
import com.intellij.ml.local.util.StorageUtil.getOrLogError
import com.intellij.ml.local.util.StorageUtil.isEmpty
import com.intellij.util.Processor
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.IntInlineKeyDescriptor
import com.intellij.util.io.PersistentMapBuilder
import java.nio.file.Path

class ClassesFrequencyStorage internal constructor(private val storageDirectory: Path,
                                                   private var isValid: Boolean) : LocalModelStorage {
  companion object {
    private const val STORAGE_NAME = "classes_frequency"
    private const val VERSION = 1
    private const val MAX_CLASSES_IN_MEMORY = 1500

    fun getStorage(baseDirectory: Path): ClassesFrequencyStorage? {
      val storageDirectory = baseDirectory.resolve(STORAGE_NAME)
      return StorageUtil.getStorage(storageDirectory, VERSION) { path, isValid -> ClassesFrequencyStorage(path, isValid) }
    }
  }
  private val persistentStorage =  PersistentMapBuilder.newBuilder(storageDirectory.resolve(STORAGE_NAME),
                                                                   EnumeratorStringDescriptor(),
                                                                   IntInlineKeyDescriptor()).compactOnClose().build()
  private val memoryStorage = mutableMapOf<String, Int>()

  @Volatile var totalClasses = 0
    private set

  @Volatile var totalClassesUsages = 0
    private set

  init {
    if (isValid) {
      toMemoryStorage()
    }
  }

  override fun name(): String = STORAGE_NAME

  override fun version(): Int = VERSION

  override fun isValid(): Boolean = isValid

  override fun isEmpty(): Boolean = persistentStorage.isEmpty()

  override fun setValid(isValid: Boolean) {
    if (isValid) {
      toMemoryStorage()
    } else {
      memoryStorage.clear()
      persistentStorage.closeAndClean()
    }
    this.isValid = isValid
    StorageUtil.saveInfo(VERSION, isValid, storageDirectory)
  }

  @Synchronized
  fun addClassUsage(className: String) {
    val existingFrequencies = persistentStorage.get(className)
    if (existingFrequencies == null) {
      persistentStorage.put(className, 1)
    } else {
      persistentStorage.put(className, existingFrequencies + 1)
    }
  }

  fun get(className: String): Int? = persistentStorage.getOrLogError(className)

  private fun toMemoryStorage() {
    memoryStorage.clear()
    totalClasses = 0
    totalClassesUsages = 0
    val sortedClasses = sortedSetOf<Pair<String, Int>>(compareBy({ it.second }, { it.first }))
    persistentStorage.processKeys(Processor {
      val count = persistentStorage.getOrLogError(it) ?: return@Processor true
      totalClasses++
      totalClassesUsages += count
      if (totalClasses > MAX_CLASSES_IN_MEMORY) {
        val first = sortedClasses.first()
        if (first.second < count) {
          sortedClasses.remove(first)
          sortedClasses.add(Pair(it, count))
        }
      } else {
        sortedClasses.add(Pair(it, count))
      }
      return@Processor true
    })
    sortedClasses.forEach { memoryStorage[it.first] = it.second }
    persistentStorage.force()
  }
}