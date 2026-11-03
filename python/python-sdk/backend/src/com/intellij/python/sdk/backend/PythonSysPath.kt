// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.util.annotations.paths.OsPath
import org.jetbrains.annotations.ApiStatus
import kotlin.io.path.pathString

private val LOG = fileLogger()

/**
 * The local paths of the `sys.path` entries [entries] that an interpreter on [eelDescriptor] printed, in their order. The
 * entries are in the eel of the interpreter, and the eel of the project can be another one. An entry that is no path is
 * skipped: `sys.path` can also hold a key of `sys.path_hooks`.
 */
@ApiStatus.Internal
fun localSysPathOf(entries: List<String>, eelDescriptor: EelDescriptor): List<@OsPath String> =
  entries.mapNotNull { entry ->
    try {
      EelPath.parse(entry, eelDescriptor)
    }
    catch (e: EelPathException) {
      LOG.debug(e) { "Can't parse $entry" }
      return@mapNotNull null
    }.asNioPath().pathString
  }
