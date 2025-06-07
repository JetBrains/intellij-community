package com.intellij.python.junit5Tests.framework.winLockedFile

import java.io.IOException
import java.nio.file.Path

/**
 * To be thrown by [deleteCheckLocking] if [path] is locked by [processes]
 */
class FileLockedException(val path: Path, val processes: Collection<ProcessHandle>)
  : IOException("""
    ${path} locked by ${processes.map { it.pid() to it.info() }.joinToString(", ")}
  """.trimIndent())