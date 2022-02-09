package com.intellij.ide.starter.ide

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.exec.ExecOutputRedirect
import com.intellij.ide.starter.exec.exec
import com.intellij.ide.starter.models.IdeProduct
import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.models.VMOptionsDiff
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.system.SystemInfo
import com.intellij.ide.starter.utils.*
import org.kodein.di.direct
import org.kodein.di.instance
import org.w3c.dom.Node
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.*
import kotlin.time.Duration


fun resolveInstalledIDE(unpackDir: File, executableFileName: String): InstalledIDE = when {
  SystemInfo.isMac -> resolveMacOSIDE(unpackDir)
  SystemInfo.isWindows -> resolveWindowsIDE(unpackDir.toPath(), executableFileName)
  SystemInfo.isLinux -> resolveLinuxIDE(unpackDir, executableFileName)
  else -> error("Not supported app: $unpackDir")
}

val xvfbRunTool by lazy {
  val toolName = "xvfb-run"

  val homePath = Path(System.getProperty("user.home")).toAbsolutePath()
  exec("xvfb-run", homePath, timeout = Duration.seconds(5), args = listOf("which", toolName),
       stdoutRedirect = ExecOutputRedirect.ToStdOut("xvfb-run-out"),
       stderrRedirect = ExecOutputRedirect.ToStdOut("xvfb-run-err"))
  toolName
}

fun linuxCommandLine(xvfbRunLog: Path): List<String> {
  return when {
    System.getenv("DISPLAY") != null -> listOf()
    else ->
      //hint https://gist.github.com/tullmann/2d8d38444c5e81a41b6d
      listOf(
        xvfbRunTool,
        "--error-file=" + xvfbRunLog.toAbsolutePath().toString(),
        "--server-args=-ac -screen 0 1920x1080x24",
        "--auto-servernum",
        "--server-num=88"
      )
  }
}

fun createXvfbRunLog(logsDir: Path): Path {
  val logTxt = logsDir.resolve("xvfb-log.txt")
  logTxt.deleteIfExists()

  return Files.createFile(logTxt)
}

private abstract class InstalledBackedIDEStartConfig(
  private val patchedVMOptionsFile: Path,
  private val finalVMOptions: VMOptions
) : IDEStartConfig {
  init {
    finalVMOptions.writeIntelliJVmOptionFile(patchedVMOptionsFile)
  }

  final override fun vmOptionsDiff(): VMOptionsDiff = finalVMOptions.diffIntelliJVmOptionFile(patchedVMOptionsFile)
}

private fun resolveLinuxIDE(unpackDir: File, executableFileName: String): InstalledIDE {
  require(SystemInfo.isLinux) { "Can only run on Linux, docker is possible, please PR" }

  val appHome = (unpackDir.listFiles()?.singleOrNull { it.isDirectory } ?: unpackDir).toPath()

  val buildTxtPath = appHome.resolve("build.txt")
  require(buildTxtPath.isRegularFile()) { "Cannot find LinuxOS IDE vmoptions file in $unpackDir" }
  val (productCode, build) = buildTxtPath.readText().trim().split("-", limit = 2)

  val binDir = appHome / "bin"
  val allBinFiles = binDir.listDirectoryEntries()
  val executablePath = allBinFiles.singleOrNull { file ->
    file.fileName.toString().equals("$executableFileName.sh")
  } ?: error("Failed to detect IDE executable .sh in:\n${allBinFiles.joinToString("\n")}")

  return object : InstalledIDE {
    override val bundledPluginsDir = appHome.resolve("plugins")

    val originalVMOptionsFile = executablePath.parent.resolve(
      executablePath.fileName.toString().removeSuffix(".sh") + "64.vmoptions") //TODO: which file to pick with 64 or without?
    override val originalVMOptions = parseVMOptions(this, originalVMOptionsFile)
    override val patchedVMOptionsFile = appHome.parent.resolve("${appHome.fileName}.vmoptions")

    override fun startConfig(vmOptions: VMOptions, logsDir: Path) =
      object : InstalledBackedIDEStartConfig(patchedVMOptionsFile, vmOptions) {

        override val environmentVariables: Map<String, String>
          get() = super.environmentVariables.filterKeys {
            when {
              it.startsWith("DESKTOP") -> false
              it.startsWith("DBUS") -> false
              it.startsWith("APPIMAGE") -> false
              it.startsWith("DEFAULTS_PATH") -> false
              it.startsWith("GDM") -> false
              it.startsWith("GNOME") -> false
              it.startsWith("GTK") -> false
              it.startsWith("MANDATORY_PATH") -> false
              it.startsWith("QT") -> false
              it.startsWith("SESSION") -> false
              it.startsWith("TOOLBOX_VERSION") -> false
              it.startsWith("XAUTHORITY") -> false
              it.startsWith("XDG") -> false
              it.startsWith("XMODIFIERS") -> false
              it.startsWith("GPG_") -> false
              it.startsWith("CLUTTER_IM_MODULE") -> false
              it.startsWith("APPDIR") -> false
              it.startsWith("LC") -> false
              it.startsWith("SSH") -> false
              else -> true
            }
          } + ("LC_ALL" to "en_US.UTF-8")

        val xvfbRunLog = createXvfbRunLog(logsDir)

        override val errorDiagnosticFiles = listOf(xvfbRunLog)
        override val workDir = appHome
        override val commandLine: List<String> = linuxCommandLine(xvfbRunLog) + executablePath.toAbsolutePath().toString()
      }

    override val build = build
    override val os = "linux"
    override val productCode = productCode
    override val isFromSources = false

    override fun toString() = "IDE{$productCode, $build, $os, home=$unpackDir}"
    override fun resolveAndDownloadTheSameJDK(): Path {
      val jbrHome = appHome.resolve("jbr")
      require(jbrHome.isDirectory()) {
        "JbrHome is not found under $jbrHome"
      }

      val jbrFullVersion = callJavaVersion(jbrHome).substringAfter("build ").substringBefore(")")
      logOutput("Found following $jbrFullVersion in the product: $productCode $build")
      // in Android Studio bundled only JRE
      if (productCode == IdeProduct.AI.ideInfo.productCode) return jbrHome
      return downloadAndUnpackJbrIfNeeded(jbrFullVersion)
    }
  }
}

fun getExecutableNameFromInfoPlist(appDir: File, @Suppress("SameParameterValue") keyName: String): String {
  val infoPlistFile = appDir.resolve("Contents/Info.plist")
  val xmlFactory = DocumentBuilderFactory.newInstance()

  infoPlistFile.inputStream().use {
    val xmlBuilder = xmlFactory.newDocumentBuilder()
    val document = xmlBuilder.parse(it)

    val keys = document.getElementsByTagName("key")

    for (index in 0 until keys.length) {
      val keyItem = keys.item(index)

      // found the node we are looking for
      if (keyItem.firstChild.nodeValue == keyName) {

        // lets find the value - it will be the next sibling
        var sibling: Node = keyItem.nextSibling
        while (sibling.nodeType != Node.ELEMENT_NODE) {
          sibling = sibling.nextSibling
        }

        return sibling.textContent
      }
    }
  }

  error("Failed to resolve key: $keyName in $infoPlistFile")
}


private fun downloadAndUnpackJbrIfNeeded(jbrFullVersion: String): Path {
  val majorVersion = jbrFullVersion.split("+").firstOrNull()?.replace(".", "_")
  requireNotNull(majorVersion) {
    { "majorVersion is: $majorVersion" }
  }
  val buildNumber = jbrFullVersion.split("-b").drop(1).singleOrNull()
  requireNotNull(buildNumber) {
    { "buildNumber is: $buildNumber" }
  }
  logOutput("Detected JBR version $jbrFullVersion with parts: $majorVersion and build $buildNumber")

  val os = when {
    SystemInfo.isWindows -> "windows"
    SystemInfo.isLinux -> "linux"
    SystemInfo.isMac -> "osx"
    else -> error("Unknown OS")
  }

  val arch = when (SystemInfo.isMac) {
    true -> when (System.getProperty("os.arch")) {
      "x86_64" -> "x64"
      "aarch64" -> "aarch64"
      else -> error("Unsupported architecture of Mac OS")
    }
    false -> "x64"
  }

  val localFileName = "jbrsdk-$majorVersion-$os-$arch-b$buildNumber.tar.gz"
  val downloadUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/$localFileName"

  val jbrCacheDirectory = di.direct.instance<GlobalPaths>().getCacheDirectoryFor("jbr")
  val localFile = jbrCacheDirectory / localFileName
  val localDir = jbrCacheDirectory / localFileName.removeSuffix(".tar.gz")

  HttpClient.downloadIfMissing(downloadUrl, localFile)
  FileSystem.unpackIfMissing(localFile, localDir)

  val appHome = (localDir.toFile().listFiles() ?: arrayOf()).singleOrNull { it.isDirectory }?.toPath()
  requireNotNull(appHome) {
    "appHome is null: $appHome"
  }
  when {
    SystemInfo.isMac -> return appHome / "Contents" / "Home"
  }
  return appHome
}

private fun resolveMacOSIDE(unpackDir: File): InstalledIDE {
  val appDir = unpackDir.listFiles()?.singleOrNull { it.name.endsWith(".app") }?.toPath() ?: error(
    "Invalid macOS application directory: $unpackDir")
  val executableName = getExecutableNameFromInfoPlist(appDir.toFile(), "CFBundleExecutable")

  val appHome = appDir.resolve("Contents")
  val executablePath = appHome / "MacOS" / executableName
  require(executablePath.isRegularFile()) { "Cannot find macOS IDE executable file in $executablePath" }

  @Suppress("SpellCheckingInspection")
  val originalVMOptions = appHome / "bin" / "$executableName.vmoptions"
  require(originalVMOptions.isRegularFile()) { "Cannot find macOS IDE vmoptions file in $executablePath" }

  val buildTxtPath = appHome / "Resources" / "build.txt"
  require(buildTxtPath.isRegularFile()) { "Cannot find macOS IDE vmoptions file in $executablePath" }

  val (productCode, build) = buildTxtPath.readText().trim().split("-", limit = 2)

  return object : InstalledIDE {
    override val bundledPluginsDir = appHome / "plugins"

    override val originalVMOptions = parseVMOptions(this, originalVMOptions)
    override val patchedVMOptionsFile = appDir.parent.resolve("${appDir.fileName}.vmoptions") //see IDEA-220286

    override fun startConfig(vmOptions: VMOptions, logsDir: Path) = object : InstalledBackedIDEStartConfig(patchedVMOptionsFile,
                                                                                                           vmOptions) {
      override val workDir = appDir
      override val commandLine = listOf(executablePath.toAbsolutePath().toString())
    }

    override val build = build
    override val os = "mac"
    override val productCode = productCode
    override val isFromSources = false

    override fun toString() = "IDE{$productCode, $build, $os, home=$appDir}"

    override fun resolveAndDownloadTheSameJDK(): Path {
      val jbrHome = appHome / "jbr"

      require(jbrHome.isDirectory()) {
        "JbrHome is not found under $jbrHome"
      }

      val javaHome = jbrHome / "Contents" / "Home"
      require(javaHome.isDirectory()) {
        "JavaHome is not found under $javaHome"
      }

      val jbrFullVersion = callJavaVersion(javaHome).substringAfter("build ").substringBefore(")")
      logOutput("Found following $jbrFullVersion in the product: $productCode $build")

      // in Android Studio bundled only JRE
      if (productCode == IdeProduct.AI.ideInfo.productCode) return jbrHome
      return downloadAndUnpackJbrIfNeeded(jbrFullVersion)
    }
  }
}

fun resolveWindowsIDE(unpackDir: Path, executableFileName: String): InstalledIDE {
  val buildTxtPath = unpackDir.resolve("build.txt")
  require(buildTxtPath.isRegularFile()) { "Cannot find WindowsOS IDE vmoptions file in $unpackDir" }
  val (productCode, build) = buildTxtPath.readText().trim().split("-", limit = 2)

  val binDir = unpackDir / "bin"

  val allBinFiles = binDir.listDirectoryEntries()

  val executablePath = allBinFiles.singleOrNull { file ->
    file.fileName.toString().equals("${executableFileName}64.exe")
  } ?: error("Failed to detect executable name, ending with 64.exe in:\n${allBinFiles.joinToString("\n")}")

  val originalVMOptionsFile = executablePath.parent.resolve("${executablePath.fileName}.vmoptions")

  return object : InstalledIDE {
    override val bundledPluginsDir = unpackDir.resolve("plugins")

    override val originalVMOptions = parseVMOptions(this, originalVMOptionsFile)
    override val patchedVMOptionsFile = unpackDir.parent.resolve("${unpackDir.fileName}.vmoptions")

    override fun startConfig(vmOptions: VMOptions, logsDir: Path) = object : InstalledBackedIDEStartConfig(patchedVMOptionsFile,
                                                                                                           vmOptions) {
      override val workDir = unpackDir
      override val commandLine = listOf(executablePath.toAbsolutePath().toString())
    }

    override val build = build
    override val os = "windows"
    override val productCode = productCode
    override val isFromSources = false

    override fun toString() = "IDE{$productCode, $build, $os, home=$unpackDir}"
    override fun resolveAndDownloadTheSameJDK(): Path {
      val jbrHome = unpackDir / "jbr"
      require(jbrHome.isDirectory()) {
        "JbrHome is not found under $jbrHome"
      }

      val jbrFullVersion = callJavaVersion(jbrHome).substringAfter("build ").substringBefore(")")
      logOutput("Found following $jbrFullVersion in the product: $productCode $build")

      // in Android Studio bundled only JRE
      if (productCode == IdeProduct.AI.ideInfo.productCode) return jbrHome
      return downloadAndUnpackJbrIfNeeded(jbrFullVersion)
    }
  }
}

fun extractIDEIfNeeded(ideInstallerFile: File, unpackDir: File) {
  if (unpackDir.isDirectory && unpackDir.listFiles()?.isNotEmpty() == true) {
    logOutput("Build directory $unpackDir already exists for the binary $ideInstallerFile")
    return
  }

  logOutput("Extracting application into $unpackDir")
  when {
    ideInstallerFile.extension == "dmg" -> unpackDmg(ideInstallerFile, unpackDir.toPath())
    ideInstallerFile.extension == "exe" -> unpackWin(ideInstallerFile, unpackDir)
    ideInstallerFile.extension == "zip" -> FileSystem.unpack(ideInstallerFile.toPath(), unpackDir.toPath())
    ideInstallerFile.name.endsWith(".tar.gz") -> FileSystem.unpackTarGz(ideInstallerFile, unpackDir)
    else -> error("Unsupported build file: $ideInstallerFile")
  }
}

private fun unpackDmg(dmgFile: File, target: Path): Path {
  target.toFile().deleteRecursively()
  target.createDirectories()

  val mountDir = File(dmgFile.path + "-mount${System.currentTimeMillis()}")
  try {
    exec(presentablePurpose = "hdiutil",
         workDir = target,
         timeout = Duration.minutes(10),
         stderrRedirect = ExecOutputRedirect.ToStdOut("hdiutil"),
         stdoutRedirect = ExecOutputRedirect.ToStdOut("hdiutil"),
         args = listOf("hdiutil", "attach", "-readonly", "-noautoopen", "-noautofsck", "-nobrowse", "-mountpoint", "$mountDir", "$dmgFile"))
  }
  catch (t: Throwable) {
    dmgFile.delete()
    throw Error("Failed to mount $dmgFile. ${t.message}.", t)
  }

  try {
    val appDir = mountDir.listFiles()?.singleOrNull { it.name.endsWith(".app") }
                 ?: error("Failed to find the only one .app folder in $dmgFile")

    val targetAppDir = target / appDir.name
    exec(
      presentablePurpose = "copy-dmg",
      workDir = target,
      timeout = Duration.minutes(10),
      stderrRedirect = ExecOutputRedirect.ToStdOut("cp"),
      args = listOf("cp", "-R", "$appDir", "$targetAppDir"))

    return targetAppDir
  }
  finally {
    catchAll {
      exec(
        presentablePurpose = "hdiutil",
        workDir = target,
        timeout = Duration.minutes(10),
        stdoutRedirect = ExecOutputRedirect.ToStdOut("hdiutil"),
        stderrRedirect = ExecOutputRedirect.ToStdOut("hdiutil"),
        args = listOf("hdiutil", "detach", "-force", "$mountDir"))
    }
  }
}

private fun unpackWin(exeFile: File, targetDir: File) {
  targetDir.deleteRecursively()

  //we use 7-Zip to unpack NSIS binaries, same way as in Toolbox App
  val sevenZipUrl = "https://repo.labs.intellij.net/thirdparty/7z-cmdline-15.06.zip"
  val sevenZipCacheDir = di.direct.instance<GlobalPaths>().getCacheDirectoryFor("7zip")

  val sevenZipFile = sevenZipCacheDir / sevenZipUrl.split("/").last()
  val sevenZipTool = sevenZipCacheDir / sevenZipFile.fileName.nameWithoutExtension

  HttpClient.downloadIfMissing(sevenZipUrl, sevenZipFile)
  FileSystem.unpackIfMissing(sevenZipFile, sevenZipTool)

  val severZipToolExe = sevenZipTool.resolve("7z.exe")

  targetDir.mkdirs()
  exec(
    presentablePurpose = "unpack-zip",
    workDir = targetDir.toPath(),
    timeout = Duration.minutes(10),
    args = listOf(severZipToolExe.toAbsolutePath().toString(), "x", "-y", "-o$targetDir", exeFile.path)
  )
}

fun parseVMOptions(ide: InstalledIDE, file: Path): VMOptions {
  return VMOptions(ide, file.readLines()
    .map { it.trim() }
    .filter { it.isNotBlank() }, emptyMap())
}

