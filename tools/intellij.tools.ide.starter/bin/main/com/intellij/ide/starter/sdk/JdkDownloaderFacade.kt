package com.intellij.ide.starter.sdk

import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.runner.SetupException
import com.intellij.ide.starter.runner.targets.TargetIdentifier
import com.intellij.ide.starter.runner.targets.isLocal
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkInstallRequest
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkInstaller
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkInstallerWSL
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkItem
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkListDownloader
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkPredicate
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.tools.ide.util.common.withRetryBlocking
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.walk

class DownloadJDKException : SetupException("JDK list is empty")

object JdkDownloaderFacade {

  val jdk8: JdkDownloadItem get() = jdkDownloader(JdkVersion.JDK_8.toString())
  val jdk11: JdkDownloadItem get() = jdkDownloader(JdkVersion.JDK_11.toString())
  val jdk17: JdkDownloadItem get() = jdkDownloader(JdkVersion.JDK_17.toString())
  val jbrJcef17: JdkDownloadItem get() = jdkDownloader(JdkVersion.JDK_17.toString(), jbr = true)
  val jdk20: JdkDownloadItem get() = jdkDownloader(JdkVersion.JDK_20.toString())
  val jdk21: JdkDownloadItem get() = jdkDownloader(JdkVersion.JDK_21.toString())
  val jbr21: JdkDownloadItem get() = jdkDownloader(JdkVersion.JDK_21.toString(), jbr = true)
  val jdk25: JdkDownloadItem get() = jdkDownloader(JdkVersion.JDK_25.toString())

  const val MINIMUM_JDK_FILES_COUNT: Int = 42

  fun jdkDownloader(version: String, jdks: Iterable<JdkDownloadItem> = allJdks, jbr: Boolean = false): JdkDownloadItem {
    val jdkName =
      when (jbr) {
        true -> "jbr"
        else -> "corretto"
      }

    return jdks.singleOrNull {
      it.jdk.sharedIndexAliases.contains("$jdkName-$version")
    } ?: throw DownloadJDKException()
  }

  val allJdks: List<JdkDownloadItem>
    get() = if (TargetIdentifier.current.isLocal()) listJDKs(JdkPredicate.forCurrentProcess())
    else listJDKs(JdkPredicate.forEel(TargetIdentifier.current.eelApi))

  private fun listJDKs(predicate: JdkPredicate): List<JdkDownloadItem> {
    val allJDKs = JdkListDownloader().downloadModelForJdkInstaller(null, predicate)
    logOutput("Total JDKs: ${allJDKs.map { it.fullPresentationText }}")

    val allVersions = allJDKs.map { it.jdkVersion }.toSortedSet()
    logOutput("JDK versions: $allVersions")

    return allJDKs.map { jdk ->
      JdkDownloadItem(jdk) {
        downloadJdkItem(jdk, predicate)
      }
    }
  }

  private fun downloadJdkItem(jdk: JdkItem, predicate: JdkPredicate): JdkItemPaths {
    val targetJdkHome = determineTargetJdkHome(predicate, jdk)
    val targetHomeMarker = targetJdkHome.resolve("home.link")
    logOutput("Checking JDK at $targetJdkHome")

    if (shouldDownloadJdk(targetJdkHome, targetHomeMarker)) {
      downloadAndInstallJdk(jdk, targetJdkHome, targetHomeMarker)
    }

    val javaHome = Path.of(Files.readString(targetHomeMarker))
    checkDownloadedJdk(javaHome, targetJdkHome)

    return JdkItemPaths(homePath = javaHome, installPath = targetJdkHome)
  }

  private fun checkDownloadedJdk(javaHome: Path, targetJdkHome: Path) {
    val javaBinary = javaHome.resolve("bin").listDirectoryEntries().sorted().firstOrNull { it.name.startsWith("java") }

    runCatching {
      require(requireNotNull(javaBinary).isRegularFile())
    }.getOrElse {
      @OptIn(ExperimentalPathApi::class)
      targetJdkHome.deleteRecursively()
      error("corrupted JDK home: $targetJdkHome (now deleted)")
    }
  }

  @Suppress("SSBasedInspection")
  private fun determineTargetJdkHome(predicate: JdkPredicate, jdk: JdkItem): Path =
    GlobalPaths.instance.getCacheDirectoryFor("jdks").resolve(jdk.installFolderName)

  private fun shouldDownloadJdk(targetJdkHome: Path, targetHomeMarker: Path): Boolean =
    !Files.isRegularFile(targetHomeMarker) || @OptIn(ExperimentalPathApi::class) targetJdkHome.walk().count() < MINIMUM_JDK_FILES_COUNT

  private fun downloadAndInstallJdk(jdk: JdkItem, targetJdkHome: Path, targetHomeMarker: Path) {
    withRetryBlocking(messageOnFailure = "Failure on downloading/installing JDK", retries = 5) {
      logOutput("Downloading JDK at $targetJdkHome")
      @OptIn(ExperimentalPathApi::class)
      targetJdkHome.deleteRecursively()

      val jdkInstaller = JdkInstaller()
      val request = jdkInstaller.prepareJdkInstallationDirect(jdk, targetPath = targetJdkHome)
      jdkInstaller.installJdk(request, targetHomeMarker)
    }
  }

  private fun downloadFileFromUrl(urlString: String, destinationPath: Path) {
    destinationPath.createParentDirectories()
    URL(urlString).openStream().use { inputStream ->
      Files.copy(inputStream, destinationPath, StandardCopyOption.REPLACE_EXISTING)
    }
  }

  private fun JdkInstaller.installJdk(request: JdkInstallRequest, markerFile: Path) {
    val item = request.item
    val targetDir = request.installDir

    val wslDistribution = wslDistributionFromPath(targetDir)
    if (wslDistribution != null && item.os != "linux") {
      error("Cannot install non-linux JDK into WSL environment to $targetDir from $item")
    }
    val temp = GlobalPaths.instance.testHomePath.resolve("tmp/jdk").toAbsolutePath().toString()
    val downloadFile = Path.of(temp, "jdk-${System.nanoTime()}-${item.archiveFileName}")
    try {
      try {
        downloadFileFromUrl(item.url, downloadFile)
        if (!downloadFile.isRegularFile()) {
          throw RuntimeException("Downloaded file does not exist: $downloadFile")
        }
      }
      catch (t: Throwable) {
        throw RuntimeException("Failed to download ${item.fullPresentationText} from ${item.url}: ${t.message}", t)
      }

      try {
        if (wslDistribution != null) {
          JdkInstallerWSL.unpackJdkOnWsl(wslDistribution, item.packageType, downloadFile, targetDir, item.packageRootPrefix)
        }
        else {
          item.packageType.openDecompressor(downloadFile).let {
            val fullMatchPath = item.packageRootPrefix.trim('/')
            if (fullMatchPath.isBlank()) it else it.removePrefixPath(fullMatchPath)
          }.extract(targetDir)
        }
      }
      catch (t: Throwable) {
        throw RuntimeException("Failed to extract ${item.fullPresentationText}. ${t.message}", t)
      }
      Files.writeString(markerFile, request.javaHome.toRealPath().toString(), Charsets.UTF_8)
    }
    finally {
      downloadFile.deleteIfExists()
    }
  }
}
