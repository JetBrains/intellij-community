package com.intellij.ide.starter.utils

import com.intellij.ide.starter.models.VMOptions
import com.intellij.openapi.util.SystemInfo
import java.nio.file.Path


  fun getUpdateEnvVarsWithAddedPath(path: Path): Map<String, String> {
    val pathEnv = if (SystemInfo.isWindows) "Path" else "PATH"
    val pathSeparator = if (SystemInfo.isWindows) ";" else ":"
    val currentPath = System.getenv().getOrDefault(pathEnv, "")

    return System.getenv() + mapOf(pathEnv to "$currentPath$pathSeparator$path")
  }

fun getUpdateEnvVarsWithPrependedPath(path: Path): Map<String, String> {
  val pathEnv = if (SystemInfo.isWindows) "Path" else "PATH"
  val pathSeparator = if (SystemInfo.isWindows) ";" else ":"
  val currentPath = System.getenv().getOrDefault(pathEnv, "")

  return System.getenv() + mapOf(pathEnv to "$path$pathSeparator$currentPath")
}

fun VMOptions.updatePathEnvVariable(path: Path){
  val pathEnv = if (SystemInfo.isWindows) "Path" else "PATH"
  val envVars = getUpdateEnvVarsWithAddedPath(path)[pathEnv]

  if (envVars != null) {
    withEnv(pathEnv, envVars)
  }
}
