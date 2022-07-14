package com.intellij.ide.starter.models

import com.intellij.ide.starter.system.OsType
import com.intellij.ide.starter.system.SystemInfo

class OsDataStorage<T>(vararg val items: Pair<OsType, T>) {
  val get: T
    get() {
      require(items.isNotEmpty()) { "Os dependent data should not be empty when accessing it" }

      val osSpecificData = when {
        SystemInfo.isMac -> items.firstOrNull { it.first == OsType.MacOS }
        SystemInfo.isWindows -> items.firstOrNull { it.first == OsType.Windows }
        SystemInfo.isLinux -> items.firstOrNull { it.first == OsType.Linux }
        else -> items.first { it.first == OsType.Other }
      }

      osSpecificData?.let { return it.second }

      return items.first { it.first == OsType.Other }.second
    }
}