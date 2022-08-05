package com.intellij.ide.starter.process.exec

import kotlin.time.Duration

class ExecTimeoutException(private val processName: String,
                           private val timeout: Duration) : RuntimeException() {
  override val message
    get() = "Failed to wait for the process `$processName` to complete in $timeout"
}