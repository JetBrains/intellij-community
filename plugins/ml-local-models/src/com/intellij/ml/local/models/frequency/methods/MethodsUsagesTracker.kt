package com.intellij.ml.local.models.frequency.methods

class MethodsUsagesTracker(private val storage: MethodsFrequencyStorage) {
  fun methodUsed(className: String, methodName: String) {
    storage.addMethodUsage(className, methodName)
  }
}