package com.intellij.ide.starter.project

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.utils.FileSystem
import com.intellij.ide.starter.utils.FileSystem.isDirUpToDate
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.ide.starter.utils.logOutput
import org.kodein.di.instance
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

interface ProjectInfoSpec {
  fun resolveProjectHome(): Path?
}

data class ProjectInfo(
  val testProjectURL: String? = null,
  val testProjectDir: Path? = null,
  val isReusable: Boolean = true,

  /**
   * can be either .zip/.tar.gz file or a directory.
   * It will be copied to a temp path before test is run
   */
  val testProjectImage: Path? = null,

  /**
   * relative path inside Image file, where project home is located
   */
  val testProjectImageRelPath: (Path) -> Path = { it }
) : ProjectInfoSpec {
  init {
    require(listOfNotNull(testProjectURL, testProjectDir, testProjectImage).size <= 1) {
      "Only one of 'testProjectURL', 'testProjectDir' or 'testProjectImage' must be specified or none"
    }
  }

  override fun resolveProjectHome(): Path? {
    if (testProjectImage == null && testProjectDir == null && testProjectURL == null) {
      return null
    }

    if (testProjectDir != null && !testProjectDir.toFile().exists()) {
      return null
    }

    if (testProjectDir != null) {
      require(testProjectURL == null)
      return testProjectDir.toAbsolutePath()
    }
    val globalPaths by di.instance<GlobalPaths>()

    val projectsUnpacked = globalPaths.getCacheDirectoryFor("projects").resolve("unpacked")
    val projectHome = projectsUnpacked.let(testProjectImageRelPath)

    if (!isReusable) {
      projectHome.toFile().deleteRecursively()
    }

    if (projectHome.isDirUpToDate()) {
      logOutput("Already unpacked project $projectHome will be used in the test")
      return projectHome
    }
    else {
      projectHome.toFile().deleteRecursively()
    }

    val imagePath: Path = if (testProjectURL != null) {
      val zipFile = when (testProjectURL.contains("https://github.com")) {
        true -> globalPaths.getCacheDirectoryFor("projects").resolve("zip").resolve("${projectHome.toString().split("/").last()}.zip")
        false -> globalPaths.getCacheDirectoryFor("projects").resolve("zip").resolve(testProjectURL.toString().split("/").last())
      }

      HttpClient.downloadIfMissing(testProjectURL, zipFile)
      zipFile
    }
    else {
      testProjectImage ?: error("Either testProjectImage or testProjectURL must be set")
    }

    when {
      imagePath.isRegularFile() -> FileSystem.unpack(imagePath, projectsUnpacked)
      imagePath.isDirectory() -> imagePath.toFile().copyRecursively(projectsUnpacked.toFile(), overwrite = true)

      else -> error("$imagePath does not exist!")
    }
    return projectHome
  }
}