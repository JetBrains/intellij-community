package com.intellij.ide.starter.utils

import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.tools.ide.util.common.logOutput
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
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
  enum class Compresssion(val value: Byte) {
    NONE(0),
    LEVEL_1(1),
    LEVEL_2(2),
    LEVEL_3(3),
    LEVEL_4(4),
    LEVEL_5(5),
    LEVEL_6(6),
    LEVEL_7(7),
    LEVEL_8(8),
    LEVEL_9(9)
  }

  val sevenZipExePath: Path by lazy {
    val sevenZipCacheDir = GlobalPaths.instance.getCacheDirectoryFor("7zip")

    // First, download an old 7-Zip distribution that is available as ZIP
    val sevenZipNineUrl = "https://www.7-zip.org/a/7za920.zip"
    val sevenZipNineFile = sevenZipCacheDir / sevenZipNineUrl.split("/").last()
    val sevenZipNineTool = sevenZipCacheDir / sevenZipNineFile.fileName.nameWithoutExtension

    HttpClient.downloadIfMissing(sevenZipNineUrl, sevenZipNineFile)
    FileSystem.unpackIfMissing(sevenZipNineFile, sevenZipNineTool)

    val sevenZipNineToolExe = sevenZipNineTool.resolve("7za.exe")

    // Then, download the new 7-Zip and unpack it using the old one
    val sevenZipUrl = "https://www.7-zip.org/a/7z2407-x64.exe"
    val sevenZipFile = sevenZipCacheDir / sevenZipUrl.split("/").last()
    val sevenZipTool = sevenZipCacheDir / sevenZipFile.fileName.nameWithoutExtension

    HttpClient.downloadIfMissing(sevenZipUrl, sevenZipFile)
    ProcessExecutor(
      presentableName = "unpack-7zip",
      workDir = sevenZipCacheDir,
      timeout = 1.minutes,
      args = listOf(sevenZipNineToolExe.absolutePathString(), "x", "-y", "-o$sevenZipTool", sevenZipFile.absolutePathString())
    ).start()

    sevenZipTool.resolve("7z.exe")
  }

  fun unpackWinMsi(exeFile: File, targetDir: File, timeout: Duration = 10.minutes) {
    targetDir.deleteRecursively()

    //we use 7-Zip to unpack NSIS binaries, same way as in Toolbox App
    targetDir.mkdirs()

    ProcessExecutor(
      presentableName = "7z-unpack-msi",
      workDir = targetDir.toPath(),
      timeout = timeout,
      args = listOf(sevenZipExePath.absolutePathString(), "x", "-y", "-o$targetDir", exeFile.path),
      stderrRedirect = ExecOutputRedirect.ToStdOut("7z-unpack-msi")
    ).start()
  }

  fun createArchive(
    source: Path,
    outputArchive: Path,
    timeout: Duration = 10.minutes,
    archiveType: String = "zip",
    compression: Compresssion = Compresssion.NONE,
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
                      "a", "-mx${compression.value}", "-mmt${Runtime.getRuntime().availableProcessors() / 2}",
                      "-t$archiveType",
                      outputArchive.absolutePathString(), source.absolutePathString(),
                      *excludes.toTypedArray()),
        stderrRedirect = ExecOutputRedirect.ToStdOut("7z-create-archive")
      ).start()
    }

    logOutput("Creating archive $outputArchive took $duration")
  }
}