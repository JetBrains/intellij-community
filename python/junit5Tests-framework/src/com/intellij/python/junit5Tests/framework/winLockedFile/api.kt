package com.intellij.python.junit5Tests.framework.winLockedFile

import com.intellij.community.wintools.getProcessLockedPath
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.python.junit5Tests.framework.winLockedFile.impl.deleteCheckLockingImpl
import com.jetbrains.python.Result
import java.io.IOException
import java.nio.file.Path

/**
 * Returns list of processes that locked [path] on Windows, much like Linux `lsof(8)` and BSD `fstat(1)`.
 * [path] must be a file or a directory accessible by user.
 * Only works on Windows, so check [SystemInfoRt.isWindows] before calling
 */
fun getProcessLockedPath(path: Path): Result<List<ProcessHandle>, @NlsSafe String> = getProcessLockedPath(path).fold(
  onSuccess = { Result.success(it) },
  onFailure = { Result.failure(it.message ?: it.toString()) }
)

/**
 * Deletes [path] throwing [FileLockedException] if a particular file is locked.
 * If process matches [processesToKillIfLocked] it is killed
 */
@Throws(IOException::class, FileLockedException::class)
fun deleteCheckLocking(path: Path, vararg processesToKillIfLocked: Regex = arrayOf(PYTHON)): Unit = deleteCheckLockingImpl(path, *processesToKillIfLocked)


private val PYTHON = Regex("^(python|pip|conda)[0-9.]*\\.exe$")

