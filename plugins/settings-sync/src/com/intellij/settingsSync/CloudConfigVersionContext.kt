package com.intellij.settingsSync

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

  fun <T> doWithVersion(version: String?, function: () -> T): T {
    val path = SETTINGS_SYNC_SNAPSHOT_ZIP
    return lock.withLock {
      try {
        if (version != null) {
          contextVersionMap[path] = version
        }

        function()
      }
      finally {
        contextVersionMap.clear()
      }
    }
  }
}