package com.intellij.ml.local.models.frequency.classes

import com.intellij.ml.local.models.api.LocalModel

class ClassesFrequencyLocalModel(private val storage: ClassesFrequencyStorage) : LocalModel {

  override val id: String = "classes_frequency"

  override fun readyToUse(): Boolean = storage.isValid() && !storage.isEmpty()

  fun totalClassesCount(): Int = storage.totalClasses

  fun totalClassesUsages(): Int = storage.totalClassesUsages

  fun getClass(className: String): Int? = storage.get(className)
}