package com.intellij.ide.starter

import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.utils.FileSystem
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.openapi.util.SystemInfo
import java.nio.file.Path
import kotlin.io.path.exists

fun downloadGoSdk(version: String): Path {
  val os = when {
    SystemInfo.isWindows -> "windows"
    SystemInfo.isLinux -> "linux"
    SystemInfo.isMac -> "darwin"
    else -> error("Unknown OS")
  }
  val arch = if (SystemInfo.isAarch64) "arm64" else "amd64"
  val extension = if (SystemInfo.isWindows) ".zip" else ".tar.gz"
  val sdkFileName = "go$version.$os-$arch$extension"
  val url = "https://cache-redirector.jetbrains.com/dl.google.com/go/$sdkFileName"
  val dirToDownload = GlobalPaths.instance.getCacheDirectoryFor("go-sdk/$version")
  val downloadedFile = dirToDownload.resolve(sdkFileName)
  val goRoot = dirToDownload.resolve("go-roots")
  if (goRoot.exists()) {
    return goRoot.resolve("go")
  }

  HttpClient.download(url, downloadedFile)
  FileSystem.unpack(downloadedFile, goRoot)
  return goRoot.resolve("go")
}