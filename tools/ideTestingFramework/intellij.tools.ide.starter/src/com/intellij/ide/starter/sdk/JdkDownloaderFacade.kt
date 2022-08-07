package com.intellij.ide.starter.sdk

import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.system.SystemInfo
import com.intellij.ide.starter.utils.logOutput
import com.intellij.ide.starter.utils.withRetry
import com.intellij.ide.starter.wsl.WslDistributionNotFoundException
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkInstaller
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkItem
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkListDownloader
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkPredicate
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.delete
import com.intellij.util.io.isDirectory
import com.intellij.util.io.readText
import com.intellij.util.io.write
import org.kodein.di.direct
import org.kodein.di.instance
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

/**
 * NOTE: this class uses IntelliJ IDEA components, please make sure
 * you call it from a proper test-case, e.g. [com.intellij.testFramework.LightPlatformTestCase]
 */
object JdkDownloaderFacade {

  val jdk8 get() = jdkDownloader(JdkVersion.JDK_8.toString())
  val jdk11 get() = jdkDownloader(JdkVersion.JDK_11.toString())
  val jdk15 get() = jdkDownloader(JdkVersion.JDK_15.toString())
  val jdk17 get() = jdkDownloader(JdkVersion.JDK_17.toString())

  fun jdkDownloader(
    version: String,
    jdks: Iterable<JdkDownloadItem> = allJdks,
  ): JdkDownloadItem {
    val jdkName = when (SystemInfo.OS_ARCH) {
      "aarch64" -> "azul"
      else -> "corretto"
    }

    return jdks.single {
      it.jdk.sharedIndexAliases.contains("$jdkName-$version")
    }
  }

  val allJdks by lazy {
    listJDKs(JdkPredicate.forCurrentProcess())
  }

  val allJdksForWSL by lazy {
    listJDKs(JdkPredicate.forWSL())
  }

  private fun listJDKs(predicate: JdkPredicate): List<JdkDownloadItem> {
    val allJDKs = JdkListDownloader.getInstance().downloadModelForJdkInstaller(null, predicate)
    logOutput("Total JDKs: ${allJDKs.map { it.fullPresentationText }}")

    val allVersions = allJDKs.map { it.jdkVersion }.toSortedSet()
    logOutput("JDK versions: $allVersions")

    return allJDKs
      .asSequence()
      .map { jdk ->
        JdkDownloadItem(jdk) {
          downloadJdkItem(jdk, predicate)
        }
      }.toList()
  }

  private fun downloadJdkItem(jdk: JdkItem, predicate: JdkPredicate): JdkItemPaths {
    val targetJdkHome: Path

    // hack for wsl on windows
    if (predicate == JdkPredicate.forWSL() && SystemInfo.isWindows && WslDistributionManager.getInstance().installedDistributions.isNotEmpty()) {
      try {
        val wslDistribution = WslDistributionManager.getInstance().installedDistributions[0]
        targetJdkHome = Path.of(wslDistribution.getWindowsPath("/tmp/jdks/${jdk.installFolderName}"))
      }
      catch (_: Exception) {
        throw WslDistributionNotFoundException(predicate)
      }
    }
    else {
      targetJdkHome = di.direct.instance<GlobalPaths>().getCacheDirectoryFor("jdks").resolve(jdk.installFolderName)
    }

    val targetHomeMarker = targetJdkHome.resolve("home.link")
    logOutput("Checking JDK at $targetJdkHome")

    if (!targetHomeMarker.isRegularFile() || !targetJdkHome.isDirectory() || (runCatching {
        Files.walk(targetJdkHome).count()
      }.getOrNull() ?: 0) < 42) {

      withRetry(retries = 5) {
        logOutput("Downloading JDK at $targetJdkHome")
        targetJdkHome.delete(true)

        val jdkInstaller = JdkInstaller.getInstance()
        val request = jdkInstaller.prepareJdkInstallationDirect(jdk, targetPath = targetJdkHome)
        jdkInstaller.installJdk(request, null, null)
        targetHomeMarker.write(request.javaHome.toRealPath().toString())
      }
    }
    val javaHome = File(targetHomeMarker.readText())
    val binJava = "bin/java" + when {
      (SystemInfo.isWindows && predicate != JdkPredicate.forWSL()) -> ".exe"
      else -> ""
    }

    require(javaHome.resolve(binJava).isFile) {
      FileUtil.delete(targetJdkHome)
      "corrupted JDK home: $targetJdkHome (now deleted)"
    }

    return JdkItemPaths(homePath = javaHome.toPath(), installPath = targetJdkHome)
  }
}
