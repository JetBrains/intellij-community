package com.intellij.ml.local.models.frequency.methods

class MethodsUsagesTracker(private val storage: MethodsFrequencyStorage) {
  private val usages = mutableListOf<Pair<String, String>>()

  fun methodUsed(className: String, methodName: String) {
    usages.add(Pair(className, methodName))
  }

  fun dump() {
    for (usage in usages) {
      storage.addMethodUsage(usage.first, usage.second)
    }
  }
}