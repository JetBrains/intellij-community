// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.exec

import kotlin.time.Duration

class ExecTimeoutException(private val processName: String,
                           private val timeout: Duration) : RuntimeException() {
  override val message
    get() = "Failed to wait for the process `$processName` to complete in $timeout"
}