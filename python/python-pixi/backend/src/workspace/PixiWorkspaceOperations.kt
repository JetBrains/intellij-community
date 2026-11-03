// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.workspace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Path

internal object PixiWorkspaceOperations {
  private class Entry(val mutex: Mutex = Mutex(), var users: Int = 0)

  private val entries = HashMap<Path, Entry>()

  suspend fun <T> run(root: Path, operation: suspend () -> T): T {
    val key = withContext(Dispatchers.IO) { root.toRealPath() }
    val entry = synchronized(entries) {
      entries.getOrPut(key) { Entry() }.also { it.users++ }
    }
    try {
      return entry.mutex.withLock { operation() }
    }
    finally {
      synchronized(entries) {
        if (--entry.users == 0) entries.remove(key)
      }
    }
  }
}
