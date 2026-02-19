package com.intellij.ide.starter.utils

import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.utils.FileSystem.deleteRecursivelyQuietly
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.system.CpuArch
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile
import kotlin.io.path.nameWithoutExtension
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.measureTime

object SevenZipWindowsArchiver {
  val sevenZipExePath: Path by lazy {
    val sevenZipCacheDir = GlobalPaths.instance.getCacheDirectoryFor("7zip")

    fun archiveInfo(arch: CpuArch): Triple<String, Path, Path> {
      val archSuffix = if (arch == CpuArch.ARM64) "arm64" else "x64"
      val url = "https://www.7-zip.org/a/7z2501-$archSuffix.exe"
      val file = sevenZipCacheDir / url.split("/").last()
      val tool = sevenZipCacheDir / file.fileName.nameWithoutExtension
      return Triple(url, file, tool)
    }

    val (sevenZipUrl, sevenZipFile, sevenZipTool) = archiveInfo(CpuArch.X86_64)
    val (sevenZipArm64Url, sevenZipArm64File, sevenZipArm64Tool) = archiveInfo(CpuArch.ARM64)
    fun sevenZipExePath(arch: CpuArch = CpuArch.CURRENT): Path = (if (arch == CpuArch.ARM64) sevenZipArm64Tool else sevenZipTool) / "7z.exe"
    if (sevenZipExePath().exists()) return@lazy sevenZipExePath()

    // First, download an old 7-Zip distribution that is available as ZIP
    val sevenZipOldUrl = "https://www.7-zip.org/a/7za920.zip"
    val sevenZipOldFile = sevenZipCacheDir / sevenZipOldUrl.split("/").last()
    val sevenZipOldTool = sevenZipCacheDir / sevenZipOldFile.fileName.nameWithoutExtension

    HttpClient.downloadIfMissing(sevenZipOldUrl, sevenZipOldFile)
    FileSystem.unpackIfMissing(sevenZipOldFile, sevenZipOldTool)

    val sevenZipOldToolExe = sevenZipOldTool.resolve("7za.exe")

    // Then, download the new 7-Zip and unpack it using the old one
    HttpClient.downloadIfMissing(sevenZipUrl, sevenZipFile)
    ProcessExecutor(
      presentableName = "unpack-7zip",
      workDir = sevenZipCacheDir,
      timeout = 1.minutes,
      //https://7-zip.opensource.jp/chm/cmdline/switches/index.htm
      args = listOf(sevenZipOldToolExe.absolutePathString(), "x", "-y", "-o$sevenZipTool", sevenZipFile.absolutePathString()),
      stderrRedirect = ExecOutputRedirect.ToStdOut("unpack-7zip"),
    ).start()

    //Old 7-zip version cannot properly unpack arm64 archive
    if (CpuArch.isArm64()) {
      HttpClient.downloadIfMissing(sevenZipArm64Url, sevenZipArm64File)
      ProcessExecutor(
        presentableName = "unpack-7zip-arm64",
        workDir = sevenZipCacheDir,
        timeout = 1.minutes,
        args = listOf(sevenZipExePath(CpuArch.X86_64).absolutePathString(), "x", "-y", "-o$sevenZipArm64Tool", sevenZipArm64File.absolutePathString()),
        stderrRedirect = ExecOutputRedirect.ToStdOut("unpack-7zip-arm64"),
      ).start()
    }
    sevenZipExePath()
  }

  @OptIn(ExperimentalPathApi::class)
  fun unpackWinMsi(exeFile: Path, targetDir: Path, timeout: Duration = 10.minutes) {
    targetDir.deleteRecursivelyQuietly()

    //we use 7-Zip to unpack NSIS binaries, same way as in Toolbox App
    targetDir.createDirectories()

    ProcessExecutor(
      presentableName = "7z-unpack-msi",
      workDir = targetDir,
      timeout = timeout,
      args = listOf(sevenZipExePath.absolutePathString(), "x", "-y", "-o$targetDir", exeFile.absolutePathString()),
      stderrRedirect = ExecOutputRedirect.ToStdOut("7z-unpack-msi")
    ).start()
  }

  /**
   * Creates an archive from the specified source using 7-Zip.
   *
   * - If the source is already a `.7z` or `.zip` file, it will be copied instead of re-archived
   * - If the output archive already exists as a non-empty regular file, archiving is skipped
   *
   * @param source The directory or file to archive
   * @param outputArchive The target archive file path
   * @param timeout Maximum time allowed for the archiving operation (default: 10 minutes)
   * @param archiveType The archive format to create (e.g., "zip", "7z"). Default is "zip"
   * @param compression Compression level from 0 to 9, where:
   *   - **0** = default: no compression (store only, fastest)
   *   - **9** = maximum compression (slowest)
   * @param excludingDirectories List of directory names to exclude from the archive (e.g., "temp", "logs").
   *   Each directory name will be matched recursively using 7-Zip's `-xr!` pattern
   */
  fun createArchive(
    source: Path,
    outputArchive: Path,
    timeout: Duration = 10.minutes,
    archiveType: String = "zip",
    compression: Int = 0,
    excludingDirectories: List<String> = emptyList(),
  ) {
    if (source.extension in listOf("7z", "zip")) {
      logOutput("Looks like $source is already an archive. Skipping archiving.")
      source.copyTo(outputArchive, overwrite = true)
      return
    }

    if (outputArchive.exists() && !outputArchive.isRegularFile() && outputArchive.fileSize() > 0) {
      logOutput("Output archive $outputArchive already exists. Skipping archiving.")
      return
    }

    //-xr!"temp" -xr!"logs"
    val excludes = excludingDirectories.map { "-xr!$it" }

    //7z a -mx0 $archiveName $directoryToCompress
    val duration = measureTime {
      ProcessExecutor(
        presentableName = "7z-create-archive",
        workDir = source,
        timeout = timeout,
        args = listOf(sevenZipExePath.absolutePathString(),
                      "a", "-mx${compression.coerceIn(0, 9)}", "-mmt${Runtime.getRuntime().availableProcessors() / 2}",
                      "-t$archiveType",
                      outputArchive.absolutePathString(), source.absolutePathString(),
                      *excludes.toTypedArray()),
        stderrRedirect = ExecOutputRedirect.ToStdOut("7z-create-archive")
      ).start()
    }

    logOutput("Creating archive $outputArchive took $duration")
  }
}