package com.intellij.ide.starter.utils

import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.utils.FileSystem.deleteRecursivelyQuietly
import com.intellij.tools.ide.util.common.logOutput
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

object SevenZipWindowsArchiver {

  val sevenZipExePath: Path by lazy { findInstalledSevenZipExe() }

  private fun findInstalledSevenZipExe(): Path {
    val installedPath = findSevenZipInPath()
    if (installedPath == null) {
      throw RuntimeException("7-Zip is not installed on the host or is not available in PATH. Install 7z.exe on the test agent and add it to PATH.")
    }
    logOutput("Using installed 7-Zip: $installedPath")
    return installedPath
  }

  private fun findSevenZipInPath(): Path? {
    val stdout = ExecOutputRedirect.ToString()
    val exitCode = ProcessExecutor(
      presentableName = "find-7zip",
      workDir = GlobalPaths.instance.localCacheDirectory,
      timeout = 10.seconds,
      args = listOf("where.exe", "7z.exe"),
      stdoutRedirect = stdout,
      stderrRedirect = ExecOutputRedirect.ToStdOut("find-7zip"),
      analyzeProcessExit = false,
      silent = true,
    ).start(printEnvVariables = false)

    if (exitCode != 0) return null

    return stdout.read().lineSequence().firstOrNull { it.isNotBlank() && Path.of(it).exists() }
      ?.let { Path.of(it) }
  }

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