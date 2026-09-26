// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.utils

import com.intellij.ide.starter.runner.SetupException

/**
 * A git command that talks to a remote failed. The remote is not a part of the test, so a test that gets
 * this is skipped. [Git] throws it from [Git.clone], [Git.fetch], [Git.pull] and [Git.push] alone.
 */
class GitRemoteException(val command: String, details: String = "", cause: Throwable? = null) : SetupException(
  buildString {
    append("The git command `$command` failed. The remote is not available.")
    if (details.isNotEmpty()) {
      appendLine()
      append(details)
    }
  },
  cause,
)
