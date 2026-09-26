package com.intellij.ide.starter.ide

import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.utils.FileSystem
import com.intellij.ide.starter.utils.FileSystem.deleteRecursivelyQuietly
import com.intellij.ide.starter.utils.FileSystem.listDirectoryEntriesQuietly
import com.intellij.ide.starter.utils.SevenZipWindowsArchiver
import com.intellij.ide.starter.utils.catchAll
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.io.delete
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.time.Duration.Companion.minutes

object IdeArchiveExtractor {

  @Deprecated("Use unpackIdeIfNeeded(ideInstallerFile: Path, unpackDir: Path)")
  fun unpackIdeIfNeeded(ideInstallerFile: File, unpackDir: File) {
    unpackIdeIfNeeded(ideInstallerFile.toPath(), unpackDir.toPath())
  }

  fun unpackIdeIfNeeded(ideInstallerFile: Path, unpackDir: Path) {
    if (unpackDir.isDirectory() && unpackDir.listDirectoryEntriesQuietly()?.isNotEmpty() == true) {
      logOutput("Build directory $unpackDir already exists for the binary $ideInstallerFile")
      return
    }

    logOutput("Extracting application into $unpackDir")
    val ext = if (ideInstallerFile.name.endsWith(".tar.gz")) "tar.gz" else ideInstallerFile.extension
    when (ext) {
      "dmg" -> unpackDmg(ideInstallerFile, unpackDir)
      "exe" -> SevenZipWindowsArchiver.unpackWinMsi(ideInstallerFile, unpackDir)
      "zip" -> FileSystem.unpack(ideInstallerFile, unpackDir)
      "tar.gz" -> FileSystem.unpackTarGz(ideInstallerFile, unpackDir)
      else -> error("Unsupported build file: $ideInstallerFile")
    }
  }

  private fun unpackDmg(dmgFile: Path, target: Path): Path {
    target.deleteRecursivelyQuietly()
    target.createDirectories()

    val mountDir = Path.of(dmgFile.absolutePathString() + "-mount${System.currentTimeMillis()}")
    try {
      ProcessExecutor(presentableName = "hdiutil",
                      workDir = target,
                      timeout = 10.minutes,
                      stderrRedirect = ExecOutputRedirect.ToStdOut("hdiutil"),
                      stdoutRedirect = ExecOutputRedirect.ToStdOut("hdiutil"),
                      args = listOf("hdiutil", "attach", "-readonly", "-noautoopen", "-noautofsck", "-nobrowse",
                                    "-mountpoint", mountDir.absolutePathString(), dmgFile.absolutePathString()),
      ).start()
    }
    catch (t: Throwable) {
      dmgFile.delete()
      throw Error("Failed to mount $dmgFile. ${t.message}.", t)
    }

    try {
      val appDir = mountDir.listDirectoryEntriesQuietly()?.singleOrNull { it.name.endsWith(".app") }
                   ?: error("Failed to find the only one .app folder in $dmgFile")

      val targetAppDir = target / appDir.name
      ProcessExecutor(
        presentableName = "copy-dmg",
        workDir = target,
        timeout = 10.minutes,
        stderrRedirect = ExecOutputRedirect.ToStdOut("cp"),
        args = listOf("cp", "-R", "$appDir", "$targetAppDir")
      ).start()

      return targetAppDir
    }
    finally {
      catchAll {
        ProcessExecutor(
          presentableName = "hdiutil",
          workDir = target,
          timeout = 10.minutes,
          stdoutRedirect = ExecOutputRedirect.ToStdOut("hdiutil"),
          stderrRedirect = ExecOutputRedirect.ToStdOut("hdiutil"),
          args = listOf("hdiutil", "detach", "-force", "$mountDir")
        ).start()
      }
    }
  }
}