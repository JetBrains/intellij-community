// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server

import com.intellij.build.FilePosition
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.HttpRequests
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole
import org.jetbrains.idea.maven.execution.SyncBundle
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.*
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

@State(name = "MavenWrapperMapping",
       storages = [Storage(value = "maven.wrapper.mapping.xml", roamingType = RoamingType.PER_OS)])
internal class MavenWrapperMapping : PersistentStateComponent<MavenWrapperMapping.State> {
  internal var myState = State()

  class State {
    @JvmField
    val mapping = HashMap<String, String>()
  }

  override fun getState() = myState

  override fun loadState(state: State) {
    myState.mapping.putAll(state.mapping)
  }

  companion object {
    @JvmStatic
    fun getInstance(): MavenWrapperMapping {
      return ApplicationManager.getApplication().getService(MavenWrapperMapping::class.java)
    }
  }
}

private const val DISTS_DIR = "wrapper/dists"

internal class MavenWrapperSupport {
  @Throws(IOException::class)
  fun downloadAndInstallMaven(urlString: String, indicator: ProgressIndicator?): MavenDistribution {
    val mapping = MavenWrapperMapping.getInstance()
    val cachedHome = mapping.myState.mapping.get(urlString)
    if (cachedHome != null) {
      val file = File(cachedHome)
      if (file.isDirectory) {
        return LocalMavenDistribution(file, urlString)
      }
      else {
        mapping.myState.mapping.remove(urlString)
      }
    }

    val zipFile = getZipFile(urlString)
    if (!zipFile.isFile) {
      val partFile = File(zipFile.parentFile, "${zipFile.name}.part-${System.currentTimeMillis()}")
      indicator?.apply { text = SyncBundle.message("maven.sync.wrapper.dowloading.from", urlString) }
      HttpRequests.request(urlString)
        .forceHttps(false)
        .connectTimeout(30_000)
        .readTimeout(30_000)
        .saveToFile(partFile, indicator)
      FileUtil.rename(partFile, zipFile)
    }

    if (!zipFile.isFile) {
      throw RuntimeException(SyncBundle.message("cannot.download.zip.from", urlString))
    }
    val home = unpackZipFile(zipFile, indicator).canonicalFile
    mapping.myState.mapping[urlString] = home.absolutePath
    return LocalMavenDistribution(home, urlString)
  }

  private fun unpackZipFile(zipFile: File, indicator: ProgressIndicator?): File {
    unzip(zipFile, indicator)
    val dirs = zipFile.parentFile.listFiles { it -> it.isDirectory }
    if (dirs == null || dirs.size != 1) {
      MavenLog.LOG.warn("Expected exactly 1 top level dir in Maven distribution, found: " + dirs?.asList())
      throw IllegalStateException(SyncBundle.message("zip.is.not.correct", zipFile.absoluteFile))
    }
    val mavenHome = dirs[0]
    if (!SystemInfo.isWindows) {
      makeMavenBinRunnable(mavenHome)
    }
    return mavenHome
  }

  private fun makeMavenBinRunnable(mavenHome: File?) {
    val mvnExe = File(mavenHome, "bin/mvn").canonicalFile
    val permissions = PosixFilePermissions.fromString("rwxr-xr-x")
    Files.setPosixFilePermissions(mvnExe.toPath(), permissions)
  }

  private fun unzip(zip: File, indicator: ProgressIndicator?) {
    indicator?.apply { text = SyncBundle.message("maven.sync.wrapper.unpacking") }
    val unpackDir = zip.parentFile
    val destinationCanonicalPath = unpackDir.canonicalPath
    var errorUnpacking = false
    try {
      ZipFile(zip).use { zipFile ->
        val entries: Enumeration<*> = zipFile.entries()
        while (entries.hasMoreElements()) {
          val entry = entries.nextElement() as ZipEntry
          val destFile = File(unpackDir, entry.name)
          val canonicalPath = destFile.canonicalPath
          if (!canonicalPath.startsWith(destinationCanonicalPath)) {
            FileUtil.delete(zip)
            throw RuntimeException("Directory traversal attack detected, zip file is malicious and IDEA dropped it")
          }

          if (entry.isDirectory) {
            destFile.mkdirs()
          }
          else {
            destFile.parentFile.mkdirs()
            BufferedOutputStream(FileOutputStream(destFile)).use {
              StreamUtil.copy(zipFile.getInputStream(entry), it)
            }
          }
        }

      }
      errorUnpacking = false
      indicator?.apply { text = SyncBundle.message("maven.sync.wrapper.unpacked.into", destinationCanonicalPath) }
    }
    finally {
      if (errorUnpacking) {
        indicator?.apply { text = SyncBundle.message("maven.sync.wrapper.failure") }
        zip.parentFile.listFiles { it -> it.name != zip.name }?.forEach { FileUtil.delete(it) }
      }
    }
  }

  private fun getZipFile(distributionUrl: String): File {
    val baseName: String = getDistName(distributionUrl)
    val distName: String = FileUtil.getNameWithoutExtension(baseName)
    val md5Hash: String = getMd5Hash(distributionUrl)
    val m2dir = MavenUtil.resolveM2Dir()
    val distsDir = File(m2dir, DISTS_DIR)

    return File(File(File(distsDir, distName), md5Hash), baseName).absoluteFile
  }

  private fun getDistName(distUrl: String): String {
    val p = distUrl.lastIndexOf("/")
    return if (p < 0) distUrl else distUrl.substring(p + 1)
  }

  private fun getMd5Hash(string: String): String {
    return try {
      val messageDigest = MessageDigest.getInstance("MD5")
      val bytes = string.toByteArray()
      messageDigest.update(bytes)
      BigInteger(1, messageDigest.digest()).toString(32)
    }
    catch (var4: Exception) {
      throw RuntimeException("Could not hash input string.", var4)
    }
  }


  companion object {
    val DISTRIBUTION_URL_PROPERTY = "distributionUrl";
    @JvmStatic
    fun getWrapperDistributionUrl(baseDir: VirtualFile?): String? {
      val wrapperProperties = getWrapperProperties(baseDir) ?: return null

      val properties = Properties()

      val stream = ByteArrayInputStream(wrapperProperties.contentsToByteArray(true))
      properties.load(stream)
      return properties.getProperty(DISTRIBUTION_URL_PROPERTY)
    }

    @JvmStatic
    fun showUnsecureWarning(console: MavenSyncConsole, mavenProjectMultimodulePath: VirtualFile?) {
      val properties = getWrapperProperties(mavenProjectMultimodulePath)

      val line = properties?.inputStream?.bufferedReader(properties.charset)?.readLines()?.indexOfFirst{ it.startsWith(DISTRIBUTION_URL_PROPERTY)} ?: -1
      val position = properties?.let { FilePosition(it.toNioPath().toFile(), line, 0) }
      console.addWarning(SyncBundle.message("maven.sync.wrapper.http.title"),
                         SyncBundle.message("maven.sync.wrapper.http.description"),
                         position)
    }


    private fun getWrapperProperties(baseDir: VirtualFile?) =
      baseDir?.findChild(".mvn")?.findChild("wrapper")?.findChild("maven-wrapper.properties")
  }
}