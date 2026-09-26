package com.intellij.settingsSync.jba

import com.intellij.util.progress.withLockCancellable
import com.jetbrains.cloudconfig.HeaderStorage
import java.util.concurrent.locks.ReentrantLock

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
    // a plain lock() parks forever behind a stuck request and makes the caller uncancellable
    return lock.withLockCancellable {
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