package com.intellij.ml.local.models.frequency.methods

import com.intellij.ml.local.models.api.LocalModelStorage
import com.intellij.ml.local.util.StorageUtil
import com.intellij.ml.local.util.StorageUtil.getOrLogError
import com.intellij.ml.local.util.StorageUtil.isEmpty
import com.intellij.util.Processor
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.PersistentMapBuilder
import java.io.DataInput
import java.io.DataOutput
import java.nio.file.Path

class MethodsFrequencyStorage internal constructor(private val storageDirectory: Path,
                                                   private var isValid: Boolean) : LocalModelStorage {
  companion object {
    private const val STORAGE_NAME = "methods_frequency"
    private const val VERSION = 1

    fun getStorage(baseDirectory: Path): MethodsFrequencyStorage? {
      val storageDirectory = baseDirectory.resolve(STORAGE_NAME)
      return StorageUtil.getStorage(storageDirectory, VERSION) { path, isValid -> MethodsFrequencyStorage(path, isValid) }
    }
  }
  private val storage = PersistentMapBuilder.newBuilder(storageDirectory.resolve(STORAGE_NAME),
                                                        EnumeratorStringDescriptor(),
                                                        MyDataExternalizer()).compactOnClose().build()

  @Volatile var totalMethods = 0
    private set

  @Volatile var totalMethodsUsages = 0
    private set

  init {
    if (isValid) {
      getTotalCounts()
    }
  }

  override fun name(): String = STORAGE_NAME

  override fun version(): Int = VERSION

  override fun isValid(): Boolean = isValid

  override fun isEmpty(): Boolean = storage.isEmpty()

  override fun setValid(isValid: Boolean) {
    if (isValid) {
      getTotalCounts()
    } else {
      storage.closeAndClean()
    }
    this.isValid = isValid
    StorageUtil.saveInfo(VERSION, isValid, storageDirectory)
  }

  @Synchronized
  fun addMethodUsage(className: String, methodName: String) {
    val existingFrequencies = storage.get(className)
    if (existingFrequencies == null) {
      storage.put(className, MethodsFrequencies.withUsage(methodName))
    } else {
      existingFrequencies.addUsage(methodName)
      storage.put(className, existingFrequencies)
    }
  }

  fun get(className: String): MethodsFrequencies? = storage.getOrLogError(className)

  private fun getTotalCounts() {
    totalMethods = 0
    totalMethodsUsages = 0
    storage.processKeys(Processor {
      val frequencies = storage.getOrLogError(it) ?: return@Processor true
      totalMethods++
      totalMethodsUsages += frequencies.getTotalFrequency()
      return@Processor true
    })
    storage.force()
  }

  private class MyDataExternalizer : DataExternalizer<MethodsFrequencies> {
    override fun save(out: DataOutput, value: MethodsFrequencies) {
      out.writeInt(value.getTotalFrequency())
      val methods = value.getMethods()
      out.writeInt(methods.size)
      for (method in methods) {
        out.writeInt(method.key)
        out.writeInt(method.value)
      }
    }

    override fun read(input: DataInput): MethodsFrequencies {
      val totalFrequency = input.readInt()
      val count = input.readInt()
      val methods = mutableMapOf<Int, Int>()
      for (i in 1..count) {
        val index = input.readInt()
        methods[index] = input.readInt()
      }
      return MethodsFrequencies(totalFrequency, methods)
    }
  }
}