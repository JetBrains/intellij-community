package com.intellij.ide.starter

import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.runner.targets.TargetIdentifier
import com.intellij.ide.starter.utils.FileSystem
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.platform.eel.isArm64
import com.intellij.platform.eel.isLinux
import com.intellij.platform.eel.isMac
import com.intellij.platform.eel.isWindows
import java.nio.file.Path
import kotlin.io.path.exists

fun downloadGoSdk(version: String): Path {
  val platform = TargetIdentifier.current.eelApi.platform
  val os = when {
    platform.isWindows -> "windows"
    platform.isLinux -> "linux"
    platform.isMac -> "darwin"
    else -> error("Unknown OS")
  }
  val arch = if (platform.isArm64) "arm64" else "amd64"
  val extension = if (platform.isWindows) ".zip" else ".tar.gz"
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