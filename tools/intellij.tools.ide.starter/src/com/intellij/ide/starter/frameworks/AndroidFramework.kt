package com.intellij.ide.starter.frameworks

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.project.GitProjectInfo
import com.intellij.ide.starter.utils.FileSystem
import com.intellij.ide.starter.utils.FileSystem.deleteRecursivelyQuietly
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.util.io.copyRecursively
import com.intellij.util.system.OS
import org.gradle.internal.hash.Hashing
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.absolutePathString
import kotlin.io.path.appendText
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.walk
import kotlin.time.Duration.Companion.minutes

class AndroidFramework(testContext: IDETestContext) : Framework(testContext) {
  companion object {
    fun downloadLatestAndroidSdk(javaHome: Path): Path {
      val packages = listOf(
        "build-tools;31.0.0",
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
      val home = GlobalPaths.instance.getCacheDirectoryFor("android-sdk") / "sdk-roots" / "sdk-root-$packagesHash"
      if (home.isDirectory() && home.walk().count() > 10) return home

      val envVariablesWithJavaHome = System.getenv() + ("JAVA_HOME" to javaHome.toAbsolutePath().toString())

      try {
        home.createDirectories()
        /// https://stackoverflow.com/questions/38096225/automatically-accept-all-sdk-licences
        /// sending "yes" to the process in the STDIN :(
        ProcessExecutor(presentableName = "android-sdk-licenses",
                        workDir = home,
                        environmentVariables = envVariablesWithJavaHome,
                        args = listOf(sdkManager.toString(), "--sdk_root=$home", "--licenses"),
                        stderrRedirect = ExecOutputRedirect.ToStdOut("[sdkmanager-err]"),
                        stdInBytes = "yes\n".repeat(10).toByteArray(), // it asks the confirmation at least two times
                        timeout = 15.minutes
        ).start()

        //loading SDK
        ProcessExecutor(presentableName = "android-sdk-loading",
                        workDir = home,
                        environmentVariables = envVariablesWithJavaHome,
                        args = listOf(sdkManager.toString(), "--sdk_root=$home", "--list"),
                        stderrRedirect = ExecOutputRedirect.ToStdOut("[sdkmanager-err]"),
                        timeout = 15.minutes
        ).start()

        //loading SDK
        ProcessExecutor(presentableName = "android-sdk-installing",
                        workDir = home,
                        environmentVariables = envVariablesWithJavaHome,
                        args = listOf(sdkManager.toString(), "--sdk_root=$home", "--install", "--verbose") + packages,
                        stderrRedirect = ExecOutputRedirect.ToStdOut("[sdkmanager-err]"),
                        timeout = 15.minutes
        ).start()
        return home
      }
      catch (t: Throwable) {
        home.deleteRecursivelyQuietly()
        throw Exception("Failed to prepare Android SDK to $home. ${t.message}", t)
      }
    }

    private fun downloadSdkManager(): Path {
      val url = when (OS.CURRENT) {
        OS.macOS -> "https://dl.google.com/android/repository/commandlinetools-mac-6200805_latest.zip"
        OS.Windows -> "https://dl.google.com/android/repository/commandlinetools-win-6200805_latest.zip"
        OS.Linux -> "https://dl.google.com/android/repository/commandlinetools-linux-6200805_latest.zip"
        else -> error("Unsupported OS: ${OS.CURRENT} ${OS.CURRENT.version()}")
      }

      val name = url.split("/").last()
      val androidSdkCache = GlobalPaths.instance.getCacheDirectoryFor("android-sdk")
      val targetArchive = androidSdkCache / "archives" / name
      val targetUnpack = androidSdkCache / "builds" / name
      HttpClient.downloadIfMissing(url, targetArchive)

      FileSystem.unpackIfMissing(targetArchive, targetUnpack)

      val ext = if (OS.CURRENT == OS.Windows) ".bat" else ""

      @Suppress("SpellCheckingInspection")
      val sdkManager = targetUnpack.walk().first { it.endsWith("tools/bin/sdkmanager$ext") }

      if (OS.CURRENT == OS.macOS || OS.CURRENT == OS.Linux) {
        val permissions = Files.getPosixFilePermissions(sdkManager)
        permissions.add(PosixFilePermission.OWNER_EXECUTE)
        Files.setPosixFilePermissions(sdkManager, permissions)
      }

      return sdkManager
    }
  }

  fun downloadAndroidPluginProjectForIJCommunity(intellijCommunityVersion: String, commit: String = "") {
    val androidProject = GitProjectInfo("ssh://git@git.jetbrains.team/ij/android.git", commit, intellijCommunityVersion, true)
      .apply { downloadAndUnpackProject() }

    // TODO: Hack because of https://youtrack.jetbrains.com/issue/AT-2013/Eel-in-Starter-Make-GitProjectInfo-and-Git-aware-of-target-eel
    val communityProjectHome = if (testContext.testCase.projectInfo is GitProjectInfo) {
      testContext.testCase.projectInfo.repositoryRootDir
    }
    else testContext.resolvedProjectHome

    val androidPluginPath = communityProjectHome / "android"
    if (androidPluginPath.exists()) return // TODO find better solution
    androidProject.repositoryRootDir.copyRecursively(androidPluginPath)
  }

  fun setupAndroidSdkToProject(androidSdkPath: Path) {
    val localPropertiesFile = testContext.resolvedProjectHome / "local.properties"
    if (!localPropertiesFile.exists()) {
      localPropertiesFile.createFile()
    }
    val path = androidSdkPath.absolutePathString().replace("""\""", """\\""")
    localPropertiesFile.appendText("${System.lineSeparator()}sdk.dir=${path}")
  }
}