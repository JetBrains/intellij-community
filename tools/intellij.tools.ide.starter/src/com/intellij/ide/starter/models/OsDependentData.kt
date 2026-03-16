package com.intellij.ide.starter.models

import com.intellij.util.system.OS

class OsDataStorage<T>(vararg val items: Pair<OS, T>) {
  val get: T
    get() {
      require(items.isNotEmpty()) { "Os dependent data should not be empty when accessing it" }

      val osSpecificData = items.firstOrNull { it.first == OS.CURRENT }
      osSpecificData?.let { return it.second }

      return items.first { it.first == OS.Other }.second
    }
}