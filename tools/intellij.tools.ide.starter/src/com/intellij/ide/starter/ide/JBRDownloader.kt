package com.intellij.ide.starter.ide

import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.runner.SetupException
import com.intellij.ide.starter.utils.FileSystem
import com.intellij.ide.starter.utils.HttpClient
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.minutes

interface JBRDownloader {
  suspend fun downloadJbr(jbrFileName: String): Path
}

object StarterJBRDownloader : JBRDownloader {
  override suspend fun downloadJbr(jbrFileName: String): Path {
    val downloadUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/$jbrFileName"

    val jbrCacheDirectory = GlobalPaths.instance.getCacheDirectoryFor("jbr")
    val localFile = jbrCacheDirectory / jbrFileName
    val localDir = jbrCacheDirectory / jbrFileName.removeSuffix(".tar.gz")

    HttpClient.downloadIfMissing(downloadUrl, localFile, retries = 1, timeout = 5.minutes)

    if (!localDir.exists()) FileSystem.unpack(localFile, jbrCacheDirectory)
    if (!localDir.exists()) throw SetupException("Couldn't find the extracted JDK folder at ${localDir.toAbsolutePath()}")

    return localDir
  }

}