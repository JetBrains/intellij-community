package com.intellij.ide.starter.ide

import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.utils.FileSystem
import com.intellij.ide.starter.utils.FileSystem.deleteRecursivelyQuietly
import com.intellij.ide.starter.utils.SevenZipWindowsArchiver
import com.intellij.ide.starter.utils.catchAll
import com.intellij.tools.ide.util.common.logOutput
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.time.Duration.Companion.minutes

object IdeArchiveExtractor {

  fun unpackIdeIfNeeded(ideInstallerFile: File, unpackDir: File) {
    if (unpackDir.isDirectory && unpackDir.listFiles()?.isNotEmpty() == true) {
      logOutput("Build directory $unpackDir already exists for the binary $ideInstallerFile")
      return
    }

    logOutput("Extracting application into $unpackDir")
    when {
      ideInstallerFile.extension == "dmg" -> unpackDmg(ideInstallerFile, unpackDir.toPath())
      ideInstallerFile.extension == "exe" -> SevenZipWindowsArchiver.unpackWinMsi(ideInstallerFile, unpackDir)
      ideInstallerFile.extension == "zip" -> FileSystem.unpack(ideInstallerFile.toPath(), unpackDir.toPath())
      ideInstallerFile.name.endsWith(".tar.gz") -> FileSystem.unpackTarGz(ideInstallerFile, unpackDir)
      else -> error("Unsupported build file: $ideInstallerFile")
    }
  }

  private fun unpackDmg(dmgFile: File, target: Path): Path {
    target.deleteRecursivelyQuietly()
    target.createDirectories()

    val mountDir = File(dmgFile.path + "-mount${System.currentTimeMillis()}")
    try {
      ProcessExecutor(presentableName = "hdiutil",
                      workDir = target,
                      timeout = 10.minutes,
                      stderrRedirect = ExecOutputRedirect.ToStdOut("hdiutil"),
                      stdoutRedirect = ExecOutputRedirect.ToStdOut("hdiutil"),
                      args = listOf("hdiutil", "attach", "-readonly", "-noautoopen", "-noautofsck", "-nobrowse", "-mountpoint", "$mountDir",
                                    "$dmgFile")
      ).start()
    }
    catch (t: Throwable) {
      dmgFile.delete()
      throw Error("Failed to mount $dmgFile. ${t.message}.", t)
    }

    try {
      val appDir = mountDir.listFiles()?.singleOrNull { it.name.endsWith(".app") }
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