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
import kotlin.io.path.name
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

object SevenZipWindowsArchiver {

  /** Absolute path to `7z.exe`, overrides both the PATH lookup and the well-known installation directories. */
  private const val SEVEN_ZIP_PATH_PROPERTY = "ide.starter.7z.path"

  val sevenZipExePath: Path by lazy { findInstalledSevenZipExe() }

  private fun findInstalledSevenZipExe(): Path {
    val explicitPath = System.getProperty(SEVEN_ZIP_PATH_PROPERTY)?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
    if (explicitPath != null && explicitPath.exists()) {
      logOutput("Using 7-Zip from -D$SEVEN_ZIP_PATH_PROPERTY: $explicitPath")
      return explicitPath
    }

    val installedPath = findSevenZipInPath()
    if (installedPath != null) {
      logOutput("Using installed 7-Zip: $installedPath")
      return installedPath
    }

    // 7-Zip is often installed but not registered in PATH, especially on Windows test agents
    val wellKnownLocations = wellKnownSevenZipLocations()
    val wellKnownPath = wellKnownLocations.firstOrNull { it.exists() }
    if (wellKnownPath != null) {
      logOutput("7z.exe is not in PATH; using well-known 7-Zip installation: $wellKnownPath")
      return wellKnownPath
    }

    throw RuntimeException("7-Zip is not installed on the host or is not available in PATH. Install 7z.exe on the test agent " +
                           "and add it to PATH, or pass -D$SEVEN_ZIP_PATH_PROPERTY=<path to 7z.exe>. " +
                           "Checked PATH via where.exe and: ${wellKnownLocations.joinToString()}")
  }

  private fun wellKnownSevenZipLocations(): List<Path> =
    listOfNotNull(
      System.getenv("ProgramFiles"),
      System.getenv("ProgramFiles(x86)"),
      System.getenv("LOCALAPPDATA")?.let { "$it\\Programs" },
    ).map { Path.of(it, "7-Zip", "7z.exe") }

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
   * @param excludingDirectories Names of directories **directly under [source]** to exclude from the archive
   *   (e.g., "temp", "logs"). Each name is anchored to the archive root before being handed to 7-Zip, so a name
   *   that also occurs deeper in the tree - a source package called `system`, say - is not excluded by accident.
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

    // `-x!` (anchored at the archive root), never `-xr!` (recursive): the `r` makes 7-Zip match the name at *any*
    // depth, so `-xr!system` also dropped the source package platform/util/src/com/intellij/util/system - which left
    // `import com.intellij.util.system.OS` unresolvable and failed the intellij-community build test with three
    // compilation errors and no hint as to why. Qualifying the pattern as `-xr!intellij-community/system` does NOT
    // help: with `r` the leading component is ignored and the nested package is still excluded.
    //-x!myProject/temp -x!myProject/logs
    val excludes = excludingDirectories.map { "-x!${source.name}/$it" }

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