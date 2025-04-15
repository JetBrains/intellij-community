// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import com.intellij.build.FilePosition
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.externalSystem.util.environment.Environment
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.zip.JBZipFile
import org.jetbrains.annotations.TestOnly
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole
import org.jetbrains.idea.maven.execution.SyncBundle
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.utils.NioFiles
import java.io.ByteArrayInputStream
import java.io.IOException
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

@State(name = "MavenWrapperMapping",
       storages = [Storage(value = "maven.wrapper.mapping.xml", roamingType = RoamingType.PER_OS)],
       category = SettingsCategory.TOOLS)
internal class MavenWrapperMapping : PersistentStateComponent<MavenWrapperMapping.State> {
  internal var myState = State()

  class State {
    @JvmField
    val mapping = ConcurrentHashMap<String, String>()
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
  fun downloadAndInstallMaven(urlString: String, indicator: ProgressIndicator?, project: Project): MavenDistribution {
    val current = getCurrentDistribution(project, urlString)
    if (current != null) return current

    val zipFile = getZipFile(urlString, project)
    if (!zipFile.exists()) {
      val partFile = zipFile.parent.resolve("${zipFile.name}.part-${System.currentTimeMillis()}")
      indicator?.apply { text = SyncBundle.message("maven.sync.wrapper.downloading.from", urlString) }
      try {
        HttpRequests.request(urlString)
          .tuner{
            val username = Environment.getVariable("MVNW_USERNAME")
            val password = Environment.getVariable("MVNW_PASSWORD")
            if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
              indicator?.apply { text = SyncBundle.message("maven.sync.wrapper.downloading.auth") }
              it.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray()))
            }
          }
          .forceHttps(false)
          .useProxy(true)
          .connectTimeout(30_000)
          .readTimeout(30_000)
          .saveToFile(partFile, indicator)
      }
      catch (t: Throwable) {
        if (t is ControlFlowException) throw RuntimeException(SyncBundle.message("maven.sync.wrapper.downloading.canceled"))
      }

      NioFiles.rename(partFile, zipFile)
    }

    if (!zipFile.exists()) {
      throw RuntimeException(SyncBundle.message("cannot.download.zip.from", urlString))
    }
    val home = unpackZipFile(zipFile, indicator).toRealPath()
    setDistributionPath(project, urlString, home)
    return LocalMavenDistribution(home, urlString)
  }

  private fun unpackZipFile(zipFile: Path, indicator: ProgressIndicator?): Path {
    unzip(zipFile, indicator)
    val dirs = zipFile.parent.listDirectoryEntries().filter { it.isDirectory() }
    if (dirs.size != 1) {
      MavenLog.LOG.warn("Expected exactly 1 top level dir in Maven distribution, found: $dirs")
      throw IllegalStateException(SyncBundle.message("zip.is.not.correct", zipFile.toAbsolutePath()))
    }
    val mavenHome = dirs[0]
    if (mavenHome.getEelDescriptor().operatingSystem == EelPath.OS.UNIX) {
      makeMavenBinRunnable(mavenHome)
    }
    return mavenHome
  }

  private fun makeMavenBinRunnable(mavenHome: Path) {
    val mvnExe = mavenHome.resolve("bin/mvn").toRealPath()
    val permissions = PosixFilePermissions.fromString("rwxr-xr-x")
    Files.setPosixFilePermissions(mvnExe, permissions)
  }

  private fun unzip(zip: Path, indicator: ProgressIndicator?) {
    indicator?.apply { text = SyncBundle.message("maven.sync.wrapper.unpacking") }
    val unpackDir = zip.parent
    val destinationCanonicalPath = unpackDir.toRealPath(LinkOption.NOFOLLOW_LINKS)
    var errorUnpacking = true
    try {
      JBZipFile(zip.toFile()).use { zipFile ->
        for (entry in zipFile.entries) {
          val entryPath = unpackDir.resolve(entry.name)
          val canonicalPath = entryPath.toAbsolutePath().normalize()
          if (!canonicalPath.startsWith(destinationCanonicalPath)) {
            Files.deleteIfExists(zip)
            throw RuntimeException("Directory traversal attack detected; zip file is malicious and has been deleted.")
          }
          if (entry.isDirectory) {
            Files.createDirectories(entryPath)
          }
          else {
            Files.createDirectories(entryPath.parent)
            entry.getInputStream().use { input ->
              Files.copy(input, entryPath, StandardCopyOption.REPLACE_EXISTING)
            }
          }
        }
      }
      errorUnpacking = false
      indicator?.apply {
        text = SyncBundle.message("maven.sync.wrapper.unpacked.into", destinationCanonicalPath)
      }
    }
    catch (e: Exception) {
      throw e
    }
    finally {
      if (errorUnpacking) {
        indicator?.apply { text = SyncBundle.message("maven.sync.wrapper.failure") }
        Files.list(unpackDir).use { stream ->
          stream.filter { it.fileName != zip.fileName }.forEach { deleteRecursively(it) }
        }
      }
    }
  }

  private fun deleteRecursively(path: Path) {
    if (Files.isDirectory(path)) {
      Files.walk(path)
        .sorted(Comparator.reverseOrder())
        .forEach { Files.deleteIfExists(it) }
    }
    else {
      Files.deleteIfExists(path)
    }
  }

  private fun getZipFile(distributionUrl: String, project: Project): Path {
    val baseName: String = getDistName(distributionUrl)
    val distName: String = FileUtil.getNameWithoutExtension(baseName)
    val md5Hash: String = getMd5Hash(distributionUrl)
    val m2dir = MavenUtil.resolveM2Dir(project)
    val distsDir = m2dir.resolve(DISTS_DIR)

    return distsDir.resolve(distName).resolve(md5Hash).resolve(baseName).toAbsolutePath()
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
    private val DISTRIBUTION_URL_PROPERTY = "distributionUrl"

    @JvmStatic
    fun getWrapperDistributionUrl(baseDir: VirtualFile?): String? {
      try {
        val wrapperProperties = getWrapperProperties(baseDir) ?: return null

        val properties = Properties()

        val stream = ByteArrayInputStream(wrapperProperties.contentsToByteArray(true))
        properties.load(stream)
        val configuredProperty = properties.getProperty(DISTRIBUTION_URL_PROPERTY)
        val urlBase = Environment.getVariable("MVNW_REPOURL")
        val configuredUrlBaseEnd = configuredProperty?.indexOf("/org/apache/maven") ?: -1
        if (!urlBase.isNullOrBlank() && configuredUrlBaseEnd >= 0) {
          return (if (urlBase.endsWith('/')) urlBase.substring(0, urlBase.length - 1) else urlBase) + configuredProperty.substring(configuredUrlBaseEnd)
        }

        return configuredProperty
      }
      catch (e: IOException) {
        MavenLog.LOG.warn("exception reading wrapper url", e)
        return null
      }

    }

    @JvmStatic
    fun showUnsecureWarning(console: MavenSyncConsole, mavenProjectMultimodulePath: VirtualFile?) {
      val properties = getWrapperProperties(mavenProjectMultimodulePath)

      val line = properties?.inputStream?.bufferedReader(properties.charset)?.readLines()?.indexOfFirst {
        it.startsWith(DISTRIBUTION_URL_PROPERTY)
      } ?: -1
      val position = properties?.let { FilePosition(it.toNioPath().toFile(), line, 0) }
      console.addWarning(SyncBundle.message("maven.sync.wrapper.http.title"),
                         SyncBundle.message("maven.sync.wrapper.http.description"),
                         position)
    }

    private fun createDistributionKey(project: Project, urlString: String): String {
      val eelDescriptor = project.getEelDescriptor()
      return if (eelDescriptor == LocalEelDescriptor) urlString else "$eelDescriptor:$urlString"
    }

    fun setDistributionPath(project: Project, urlString: String, path: Path) {
      MavenWrapperMapping.getInstance().myState.mapping[createDistributionKey(project, urlString)] = path.toAbsolutePath().toString()
    }

    @JvmStatic
    fun getCurrentDistribution(project: Project, urlString: String): MavenDistribution? {
      val mapping = MavenWrapperMapping.getInstance()
      val key = createDistributionKey(project, urlString)
      val cachedHome = mapping.myState.mapping[key]
      if (cachedHome != null) {
        val path = Path.of(cachedHome)
        if (path.isDirectory()) {
          return LocalMavenDistribution(path, urlString)
        }
        else {
          mapping.myState.mapping.remove(key)
        }
      }
      return null
    }

    @JvmStatic
    fun getWrapperProperties(baseDir: VirtualFile?) =
      baseDir?.findChild(".mvn")?.findChild("wrapper")?.findChild("maven-wrapper.properties")
  }
}