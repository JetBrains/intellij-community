package com.intellij.ml.local.models.frequency.methods

import com.intellij.ml.local.models.api.LocalModel

class MethodsFrequencyLocalModel internal constructor(private val storage: MethodsFrequencyStorage) : LocalModel {

  override val id: String = "methods_frequency"

  override fun readyToUse(): Boolean = storage.isValid() && !storage.isEmpty()

  fun totalMethodsCount(): Int = storage.totalMethods

  fun totalMethodsUsages(): Int = storage.totalMethodsUsages

  fun getMethodsByClass(className: String): MethodsFrequencies? = storage.get(className)
}