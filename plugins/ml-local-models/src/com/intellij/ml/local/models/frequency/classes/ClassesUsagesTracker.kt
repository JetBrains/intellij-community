package com.intellij.ml.local.models.frequency.classes

class ClassesUsagesTracker(private val storage: ClassesFrequencyStorage) {
  fun classUsed(className: String) {
    storage.addClassUsage(className)
  }
}