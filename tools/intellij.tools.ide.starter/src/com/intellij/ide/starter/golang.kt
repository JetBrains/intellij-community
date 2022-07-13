package com.intellij.ide.starter

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.system.SystemInfo
import com.intellij.ide.starter.utils.FileSystem
import com.intellij.ide.starter.utils.HttpClient
import org.kodein.di.direct
import org.kodein.di.instance
import java.nio.file.Path

fun downloadGoSdk(version: String): Path {
  val os = when {
    SystemInfo.isWindows -> "windows"
    SystemInfo.isLinux -> "linux"
    SystemInfo.isMac -> "darwin"
    else -> error("Unknown OS")
  }
  val extension = when {
    SystemInfo.isWindows -> ".zip"
    SystemInfo.isLinux || SystemInfo.isMac -> ".tar.gz"
    else -> error("Unknown OS")
  }
  val url = "https://cache-redirector.jetbrains.com/dl.google.com/go/go$version.$os-amd64$extension"
  val dirToDownload = di.direct.instance<GlobalPaths>().getCacheDirectoryFor("go-sdk/$version")
  val downloadedFile = dirToDownload.resolve("go$version.$os-amd64$extension")
  val goRoot = dirToDownload.resolve("go-roots")
  if (goRoot.toFile().exists()) {
    return goRoot.resolve("go")
  }

  HttpClient.download(url, downloadedFile)
  FileSystem.unpack(downloadedFile, goRoot)
  return goRoot.resolve("go")
}