package com.intellij.ide.starter.process.exec

import kotlin.time.Duration

class ExecTimeoutException(override val message: String) : RuntimeException() {
  constructor (processName: String, timeout: Duration)
    : this(message = "Failed to wait for the process `$processName` to complete in $timeout")

  override val cause: Throwable?
    get() = null

  override fun getStackTrace(): Array<StackTraceElement> {
    return arrayOf()
  }
}