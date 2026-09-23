package com.intellij.ide.starter.runner

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.path.FrontendIDEDataPaths
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.platform.testFramework.teamCity.TeamCityReporter.SyntheticTestKind
import com.intellij.tools.ide.util.common.logOutput
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.name


/**
 * Reads the modification time of each directory of [startupDirs] itself, just before the launch.
 *
 * At each start, the IDE creates and deletes files directly in its config and system directories, see `DirectoryLock`
 * and `checkDirectories` in `startup.kt`. Each of these changes moves the time of the directory, also when the
 * directory is seeded from a baseline. A directory that does not exist yet gets the epoch, because the IDE creates it.
 */
internal fun readDirWatermarks(paths: IDEDataPaths): Map<Path, FileTime> =
  startupDirs(paths).associateWith { dir -> readModificationTime(dir) ?: FileTime.fromMillis(0) }

/**
 * The config and the system directory that the IDE process uses.
 *
 * A frontend process does not use the directories of its `.vmoptions`. It takes per-process directories below its
 * system directory, so its own config and system directories do not change at a start.
 */
private fun startupDirs(paths: IDEDataPaths): List<Path> =
  if (paths is FrontendIDEDataPaths) listOf(paths.perProcessConfigDir, paths.perProcessSystemDir)
  else listOf(paths.configDir, paths.systemDir)

/**
 * Whether the IDE prints an answer to [ideArgs] and exits before its start, so it never writes to its directories.
 *
 * It repeats `preprocessArgs` in `Main.kt`. That function skips the `-D` options and the well-known start arguments,
 * then `-e` or `--edit`, and checks the first argument that is left.
 */
internal fun isInformationalLaunch(ideArgs: List<String>): Boolean {
  val args = ideArgs.filterNot { (it.startsWith("-D") && '=' in it) || it.isWellKnownStartArgument() }
  val firstArg = if (args.firstOrNull() == "-e" || args.firstOrNull() == "--edit") args.getOrNull(1) else args.firstOrNull()
  return firstArg in INFORMATIONAL_ARGUMENTS
}

/** The arguments of `ApplicationStartArguments`. */
private fun String.isWellKnownStartArgument(): Boolean =
  equals("disableNonBundledPlugins", ignoreCase = true) || equals("dontReopenProjects", ignoreCase = true) ||
  this == "splash" || this == "nosplash"

private val INFORMATIONAL_ARGUMENTS = setOf("--help", "-h", "-?", "--list-commands", "--version", "-version", "-v")

/** Reports a synthetic failure for each directory of [watermarks] the IDE did not change. */
internal fun validateVMOptionsWereSet(watermarks: Map<Path, FileTime>) {
  logOutput("Run VM options validation")
  watermarks.forEach { (dir, watermark) -> validateDirWasChanged(dir, watermark) }
  logOutput("Finished VM options validation")
}

private fun validateDirWasChanged(dir: Path, watermark: FileTime) {
  val modified = readModificationTime(dir)
  if (modified != null && modified > watermark) {
    logOutput("VM options validation: the IDE changed the ${dir.name} directory $dir after $watermark")
    return
  }
  CIServer.instance.reportTestFailure(
    testName = "IDE must have written files under ${dir.name} directory at $dir. Were .vmoptions included correctly?",
    message = if (modified == null) "The ${dir.name} directory does not exist after the run"
    else "The ${dir.name} directory did not change after $watermark, when the IDE was launched",
    details = "", kind = SyntheticTestKind.TEST_INFRA_EXCEPTION)
}

/** The modification time of [dir] itself, or null when [dir] does not exist or cannot be read. */
private fun readModificationTime(dir: Path): FileTime? =
  try {
    Files.getLastModifiedTime(dir)
  }
  catch (_: IOException) {
    null
  }
