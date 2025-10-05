package com.intellij.python.junit5Tests.framework.winLockedFile.impl

import com.intellij.community.wintools.WinProcessInfo
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.python.junit5Tests.framework.winLockedFile.FileLockedException
import com.intellij.python.junit5Tests.framework.winLockedFile.getProcessLockedPath
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.isDirectory
import kotlin.jvm.optionals.getOrNull


private val fileLogger = fileLogger()

@OptIn(ExperimentalPathApi::class)
@Throws(IOException::class, FileLockedException::class)
internal fun deleteCheckLockingImpl(path: Path, vararg processesToKill: Regex) {
  try {
    path.deleteRecursively()
  }
  catch (e: IOException) {
    if (!SystemInfoRt.isWindows) {
      throw e
    }

    val paths = listOf(path) + if (path.isDirectory()) Files.walk(path).use { it.toList() } else emptyList()
    // First, kill processes
    for (child in paths) {
      for (process in getProcessLockedPath(child).orThrow()) {
        val command = process.info().command().getOrNull() ?: continue
        val fileName = Path.of(command).fileName.toString()
        if (processesToKill.any { it.matches(fileName) }) {
          val processInfo = WinProcessInfo.get(process.pid())
          fileLogger.warn("Killing ${process.pid()} ${processInfo}")
          killProcess(process)
        }
        else {
          fileLogger.warn("Process ${WinProcessInfo.get(process.pid())} locks file $child, but I can't kill it as it doesnt match ${processesToKill.joinToString(",")}")
        }
      }
    }

    // Then wait a little and see if paths are still locked
    Thread.sleep(1000)
    for (child in paths) {
      val processes = getProcessLockedPath(child).orThrow()
      if (processes.isNotEmpty()) {
        throw FileLockedException(path, processes)
      }
    }
  }
}

private fun killProcess(process: ProcessHandle) {
  process.destroy()
  Thread.sleep(100)
  if (!process.isAlive) return

  repeat(10) {
    process.destroyForcibly()
    Thread.sleep(100)
    if (!process.isAlive) return
  }
  fileLogger().warn("After second, process ${process.pid()} ${process.info()} is still alive")
}