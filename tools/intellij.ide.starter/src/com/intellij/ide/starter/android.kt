package com.intellij.ide.starter

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.exec.ExecOutputRedirect
import com.intellij.ide.starter.exec.exec
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.system.SystemInfo
import com.intellij.ide.starter.utils.FileSystem
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.ide.starter.utils.logOutput
import com.intellij.ide.starter.utils.resolveInstalledJdk11
import org.gradle.internal.hash.Hashing
import org.kodein.di.direct
import org.kodein.di.instance
import java.io.File
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.time.Duration

/**
 * Resolve platform specific android studio installer and return paths
 * @return Pair<InstallDir / InstallerFile>
 */
fun downloadAndroidStudio(): Pair<Path, File> {
  val ext = when {
    SystemInfo.isWindows -> "-windows.zip"
    SystemInfo.isMac -> "-mac.zip"
    SystemInfo.isLinux -> "-linux.tar.gz"
    else -> error("Not supported OS")
  }

  val downloadUrl = "https://redirector.gvt1.com/edgedl/android/studio/ide-zips/2021.1.1.11/android-studio-2021.1.1.11" + ext
  val asFileName = downloadUrl.split("/").last()
  val globalPaths by di.instance<GlobalPaths>()
  val zipFile = globalPaths.getCacheDirectoryFor("android-studio").resolve(asFileName)
  HttpClient.downloadIfMissing(downloadUrl, zipFile)

  val installDir = globalPaths.getCacheDirectoryFor("builds") / "AI-211"

  installDir.toFile().deleteRecursively()

  val installerFile = zipFile.toFile()

  return Pair(installDir, installerFile)
}

fun downloadLatestAndroidSdk(javaHome: Path): Path {
  val packages = listOf(
    "build-tools;28.0.3",
    //"cmake;3.10.2.4988404",
    //"docs",
    //"ndk;20.0.5594570",
    "platforms;android-28",
    "sources;android-28",
    "platform-tools"
  )

  val sdkManager = downloadSdkManager()

  // we use unique home folder per installation to ensure only expected
  // packages are included into the SDK home path
  val packagesHash = Hashing.sha1().hashString(packages.joinToString("$"))
  val home = di.direct.instance<GlobalPaths>().getCacheDirectoryFor("android-sdk") / "sdk-roots" / "sdk-root-$packagesHash"
  if (home.isDirectory() && home.toFile().walk().count() > 10) return home

  val envVariablesWithJavaHome = System.getenv() + ("JAVA_HOME" to javaHome.toAbsolutePath().toString())

  try {
    home.toFile().mkdirs()
    /// https://stackoverflow.com/questions/38096225/automatically-accept-all-sdk-licences
    /// sending "yes" to the process in the STDIN :(
    exec(presentablePurpose = "android-sdk-licenses",
         workDir = home,
         environmentVariables = envVariablesWithJavaHome,
         args = listOf(sdkManager.toString(), "--sdk_root=$home", "--licenses"),
         stderrRedirect = ExecOutputRedirect.ToStdOut("[sdkmanager-err]"),
         stdInBytes = "yes\n".repeat(10).toByteArray(), // it asks the confirmation at least two times
         timeout = Duration.minutes(15)
    )

    //loading SDK
    exec(presentablePurpose = "android-sdk-loading",
         workDir = home,
         environmentVariables = envVariablesWithJavaHome,
         args = listOf(sdkManager.toString(), "--sdk_root=$home", "--list"),
         stderrRedirect = ExecOutputRedirect.ToStdOut("[sdkmanager-err]"),
         timeout = Duration.minutes(15)
    )

    //loading SDK
    exec(presentablePurpose = "android-sdk-installing",
         workDir = home,
         environmentVariables = envVariablesWithJavaHome,
         args = listOf(sdkManager.toString(), "--sdk_root=$home", "--install", "--verbose") + packages,
         stderrRedirect = ExecOutputRedirect.ToStdOut("[sdkmanager-err]"),
         timeout = Duration.minutes(15)
    )
    return home
  }
  catch (t: Throwable) {
    home.toFile().deleteRecursively()
    throw Exception("Failed to prepare Android SDK to $home. ${t.message}", t)
  }
}

private fun downloadSdkManager(): Path {
  val url = when {
    SystemInfo.isMac -> "https://dl.google.com/android/repository/commandlinetools-mac-6200805_latest.zip"
    SystemInfo.isWindows -> "https://dl.google.com/android/repository/commandlinetools-win-6200805_latest.zip"
    SystemInfo.isLinux -> "https://dl.google.com/android/repository/commandlinetools-linux-6200805_latest.zip"
    else -> error("Unsupported OS: ${SystemInfo.OS_NAME} ${SystemInfo.OS_VERSION}")
  }

  val name = url.split("/").last()
  val androidSdkCache = di.direct.instance<GlobalPaths>().getCacheDirectoryFor("android-sdk")
  val targetArchive = androidSdkCache / "archives" / name
  val targetUnpack = androidSdkCache / "builds" / name
  HttpClient.downloadIfMissing(url, targetArchive)

  FileSystem.unpackIfMissing(targetArchive, targetUnpack)

  val ext = if (SystemInfo.isWindows) ".bat" else ""

  @Suppress("SpellCheckingInspection")
  val sdkManager = targetUnpack.toFile().walk().first { it.endsWith("tools/bin/sdkmanager$ext") }

  if (SystemInfo.isMac || SystemInfo.isLinux) {
    sdkManager.setExecutable(true)
  }

  return sdkManager.toPath()
}

fun main() {
  downloadLatestAndroidSdk(resolveInstalledJdk11())
}

fun IDETestContext.downloadAndroidPluginProject(): IDETestContext {
  val projectHome = resolvedProjectHome
  if (projectHome.toFile().name == "intellij-community-master" && !(projectHome / "android").toFile().exists()) {
    val scriptName = "getPlugins.sh"

    val script = (projectHome / scriptName).toFile()
    assert(script.exists()) { "File $script does not exist" }
    val scriptContent = script.readText()

    val stdout = ExecOutputRedirect.ToString()
    exec(
      "git-clone-android-plugin",
      workDir = projectHome, timeout = Duration.minutes(10),
      args = scriptContent.split(" "),
      stdoutRedirect = stdout
    )
    logOutput(stdout.read().trim())
  }
  return this
}

