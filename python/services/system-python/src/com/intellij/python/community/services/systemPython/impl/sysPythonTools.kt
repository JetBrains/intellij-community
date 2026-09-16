// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.systemPython.impl

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.python.community.services.systemPython.SysPythonRegisterError
import com.intellij.python.sdk.backend.PythonEnvironment
import com.intellij.python.sdk.backend.SystemPythonEnvironment
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyError
import java.io.IOException
import java.nio.file.FileSystemNotFoundException
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import org.jetbrains.annotations.ApiStatus.Internal

private val logger = fileLogger()

internal fun PyError.asSysPythonRegisterError(): SysPythonRegisterError.PythonIsBroken = SysPythonRegisterError.PythonIsBroken(this)

/**
 * Whether this is a system-wide python installation, and not an environment such as a virtual environment.
 *
 * The file system layout answers this, so no interpreter starts. The previous check ran
 * `sys.prefix == sys.base_prefix`, which started every candidate, see PY-88315.
 */
internal val PythonEnvironment.isSystemPython: Boolean get() = this is SystemPythonEnvironment

/**
 * The state of a python binary file: the size of the file and the time of its last change.
 */
@Internal
data class BinaryStamp(val size: Long, val lastModified: FileTime)

/**
 * The current [BinaryStamp] of this binary, or `null` when the attributes of the file can not be read.
 *
 * The link is followed, so the answer describes the file that runs. A distribution points `python` at `python3` and
 * `python3` at `python3.11`; when it later points `python3` at `python3.13`, the first link keeps its own time, but
 * this reads the new target and the stamp changes.
 *
 * `null` is a normal answer, not a failure. A python from the Windows Store is a reparse point whose attributes
 * Windows does not give, and an eel file system reports a file it no longer serves. The caller reads the
 * information of such an interpreter each time instead.
 */
@Internal
@RequiresBackgroundThread
fun PythonBinary.binaryStamp(): BinaryStamp? =
  try {
    val attributes = Files.readAttributes(this, BasicFileAttributes::class.java)
    BinaryStamp(attributes.size(), attributes.lastModifiedTime())
  }
  catch (e: IOException) {
    logger.debug(e) { "Cannot read the attributes of $this" }
    null
  }
  catch (e: FileSystemNotFoundException) {
    // An eel file system can be deregistered while a search runs, and this exception is unchecked.
    logger.debug(e) { "No file system for $this" }
    null
  }
