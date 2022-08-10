package com.intellij.ide.starter.utils

import com.intellij.ide.starter.system.SystemInfo
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.div
import kotlin.io.path.isDirectory

fun createInMemoryDirectory(directoryName: String): Path {
  require(SystemInfo.isLinux)
  val shmDirectory = Paths.get("/dev/shm")
  check(shmDirectory.isDirectory())
  return shmDirectory / directoryName
}