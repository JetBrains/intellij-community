package com.intellij.ml.local.models.frequency.classes

import com.intellij.ml.local.models.api.LocalModelStorage
import com.intellij.ml.local.util.StorageUtil
import com.intellij.ml.local.util.StorageUtil.clear
import com.intellij.ml.local.util.StorageUtil.isEmpty
import com.intellij.util.Processor
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.IntInlineKeyDescriptor
import com.intellij.util.io.PersistentHashMap
import java.nio.file.Path

class ClassesFrequencyStorage internal constructor(private val storageDirectory: Path) : LocalModelStorage {
  companion object {
    private const val STORAGE_NAME = "classes_frequency"
    private const val VERSION = 1
    private const val CLASS_FREQUENCY_THRESHOLD = 5

    fun getStorage(baseDirectory: Path): ClassesFrequencyStorage {
      val storageDirectory = baseDirectory.resolve(STORAGE_NAME)
      StorageUtil.prepareStorage(storageDirectory, VERSION)
      return ClassesFrequencyStorage(storageDirectory)
    }
  }
  private var isValid: Boolean = true
  private val persistentStorage = PersistentHashMap(storageDirectory.resolve(STORAGE_NAME),
                                                    EnumeratorStringDescriptor(),
                                                    IntInlineKeyDescriptor())
  private val memoryStorage = mutableMapOf<String, Int>()

  @Volatile var totalClasses = 0
    private set

  @Volatile var totalClassesUsages = 0
    private set

  init {
    toMemoryStorage()
  }

  override fun name(): String = STORAGE_NAME

  override fun version(): Int = VERSION

  override fun isValid(): Boolean = isValid

  override fun isEmpty(): Boolean = persistentStorage.isEmpty()

  override fun setValid(isValid: Boolean) {
    if (isValid) {
      toMemoryStorage()
    } else {
      persistentStorage.clear()
      memoryStorage.clear()
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

  fun get(className: String): Int? = persistentStorage.get(className)

  private fun toMemoryStorage() {
    memoryStorage.clear()
    totalClasses = 0
    totalClassesUsages = 0
    persistentStorage.processKeys(Processor {
      val count = persistentStorage.get(it) ?: return@Processor true
      totalClasses++
      totalClassesUsages += count
      if (count >= CLASS_FREQUENCY_THRESHOLD) {
        memoryStorage[it] = count
      }
      return@Processor true
    })
    persistentStorage.force()
  }
}