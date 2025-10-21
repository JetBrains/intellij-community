package org.jetbrains.plugins.textmate.concurrent

interface TextMateLock {
  fun <T> withLock(body: () -> T): T
}