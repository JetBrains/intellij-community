// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server

import com.intellij.openapi.components.*
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.HttpRequests
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
import kotlin.collections.HashMap


@State(name = "MavenWrapperMapping",
       storages = [Storage(value = "maven.wrapper.mapping.xml", roamingType = RoamingType.PER_OS)])
class MavenWrapperMapping : PersistentStateComponent<MavenWrapperMapping.State> {
  internal var myState = State()

  class State {
    val mapping = HashMap<String, String>()
  }

  override fun getState(): State? {
    return myState
  }

  override fun loadState(state: State) {
    myState.mapping.putAll(state.mapping)
  }

  companion object {
    @JvmStatic
    fun getInstance(): MavenWrapperMapping {
      return ServiceManager.getService(MavenWrapperMapping::class.java)
    }
  }
}

class MavenWrapperSupport {

  private val myMapping = MavenWrapperMapping.getInstance()
  val DISTS_DIR = "wrapper/dists"

  @Throws(IOException::class)
  fun downloadAndInstallMaven(urlString: String): MavenDistribution {
    val cachedHome = myMapping.myState.mapping.get(urlString)
    if (cachedHome != null) {
      val file = File(cachedHome)
      if (file.isDirectory) {
        return MavenDistribution(file, urlString)
      }
      else {
        myMapping.myState.mapping.remove(urlString)
      }
    }


    val zipFile = getZipFile(urlString)
    if (!zipFile.isFile) {
      val partFile = File(zipFile.parentFile, "${zipFile.name}.part-${System.currentTimeMillis()}")
      HttpRequests.request(urlString)
        .forceHttps(true)
        .connectTimeout(30_000)
        .readTimeout(30_000)
        .saveToFile(partFile, null) //todo: cancel and progress
      FileUtil.rename(partFile, zipFile)
    }
    if (!zipFile.isFile) {
      throw RuntimeException(SyncBundle.message("cannot.download.zip.from", urlString))
    }
    val home = unpackZipFile(zipFile).canonicalFile
    myMapping.myState.mapping[urlString] = home.absolutePath
    return MavenDistribution(home, urlString)

  }


  private fun unpackZipFile(zipFile: File): File {
    unzip(zipFile)
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

  private fun unzip(zip: File) {
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
              StreamUtil.copyStreamContent(zipFile.getInputStream(entry), it)
            }
          }
        }

      }
      errorUnpacking = false
    }
    finally {
      if (errorUnpacking) {
        zip.parentFile.listFiles { it -> it.name != zip.name }?.forEach { FileUtil.delete(it) }
      }
    }

  }


  fun getZipFile(distributionUrl: String): File {
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
    @JvmStatic
    fun hasWrapperConfigured(baseDir: VirtualFile): Boolean {
      return !getWrapperDistributionUrl(baseDir).isNullOrEmpty()
    }

    @JvmStatic
    fun getWrapperDistributionUrl(baseDir: VirtualFile?): String? {
      val wrapperProperties = baseDir?.findChild(".mvn")?.findChild("wrapper")?.findChild("maven-wrapper.properties") ?: return null

      val properties = Properties()

      val stream = ByteArrayInputStream(wrapperProperties.contentsToByteArray(true))
      properties.load(stream)
      return properties.getProperty("distributionUrl")
    }
  }
}