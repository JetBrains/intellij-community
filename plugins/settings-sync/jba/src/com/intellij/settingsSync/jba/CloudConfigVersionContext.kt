package com.intellij.settingsSync.jba

import com.jetbrains.cloudconfig.HeaderStorage
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class CloudConfigVersionContext : HeaderStorage {
  private val contextVersionMap = mutableMapOf<String, String>()
  private val lock = ReentrantLock()

  override fun get(path: String): String? {
    return contextVersionMap[path]
  }

  override fun store(path: String, value: String) {
    contextVersionMap[path] = value
  }

  override fun remove(path: String?) {
    contextVersionMap.remove(path)
  }

  fun <T> doWithVersion(filePath: String, version: String?, function: (String) -> T): T {
    return lock.withLock {
      try {
        if (version != null) {
          contextVersionMap[filePath] = version
        }

        function(filePath)
      }
      finally {
        contextVersionMap.clear()
      }
    }
  }
}