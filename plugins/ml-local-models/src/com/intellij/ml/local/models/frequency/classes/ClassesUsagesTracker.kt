package com.intellij.ml.local.models.frequency.classes

class ClassesUsagesTracker(private val storage: ClassesFrequencyStorage) {
  private val usages = mutableListOf<String>()

  fun classUsed(className: String) {
    usages.add(className)
  }

  fun dump() {
    for (usage in usages) {
      storage.addClassUsage(usage)
    }
  }
}