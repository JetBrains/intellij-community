package com.intellij.ide.starter.project

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.runner.SetupException
import com.intellij.ide.starter.utils.FileSystem
import com.intellij.ide.starter.utils.FileSystem.deleteRecursivelyQuietly
import com.intellij.ide.starter.utils.FileSystem.isDirUpToDate
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.ThreeState
import com.intellij.util.io.zip.JBZipFile
import org.kodein.di.instance
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Project stored on a remote server as an archive
 */
data class RemoteArchiveProjectInfo(
  val projectURL: String,
  override val isReusable: Boolean = false,
  override val downloadTimeout: Duration = 10.minutes,
  override val configureProjectBeforeUse: (IDETestContext) -> Unit = {},
  /**
   * Relative path inside top-level archive directory, where project home is located
   */
  val projectHomeRelativePath: (Path) -> Path = { it },
  private val description: String = "",
) : ProjectInfoSpec {

  private fun getTopMostFolderFromZip(zipFile: Path): String = JBZipFile(zipFile, StandardCharsets.UTF_8, false, ThreeState.UNSURE).entries.first().name.split("/").first()

  @OptIn(ExperimentalPathApi::class)
  override fun downloadAndUnpackProject(): Path {
    val globalPaths by di.instance<GlobalPaths>()

    val projectsUnpacked = globalPaths.cacheDirForProjects.resolve("unpacked").createDirectories()

    val zipFile = globalPaths.cacheDirForProjects.resolve("zip").resolve(projectURL.transformUrlToZipName())

    HttpClient.downloadIfMissing(url = projectURL, targetFile = zipFile, timeout = downloadTimeout)
    val imagePath: Path = zipFile

    if (!imagePath.isRegularFile()) {
      throw SetupException("Failed to download the project")
    }

    val projectHome = (projectsUnpacked / getTopMostFolderFromZip(zipFile)).let(projectHomeRelativePath)

    if (!isReusable) {
      val isDeleted = projectHome.deleteRecursivelyQuietly()
      if (!isDeleted) {
        logOutput("Failed to delete $projectHome")
      }
    }

    if (projectHome.isDirUpToDate()) {
      logOutput("Already unpacked project $projectHome will be used in the test")
      return projectHome
    }
    else {
      projectHome.deleteRecursivelyQuietly()
    }

    when {
      imagePath.isRegularFile() -> FileSystem.unpack(imagePath, projectsUnpacked)
      imagePath.isDirectory() -> imagePath.copyToRecursively(projectsUnpacked, followLinks = true, overwrite = true)

      else -> error("$imagePath does not exist!")
    }

    return projectHome
  }

  private fun String.transformUrlToZipName(): String {
    return when {
      projectURL.contains("https://github.com") -> {
        this.removePrefix("https://github.com/").split("/").joinToString("_", postfix = ".zip")
      }
      projectURL.contains("https://kmp.jetbrains.com") -> {
        val regex = Regex("""[?&]name=([^&]+)""", RegexOption.IGNORE_CASE)
        val nameEncoded = regex.find(this)?.groupValues?.getOrNull(1)
        val name = nameEncoded
          ?.let { java.net.URLDecoder.decode(it, StandardCharsets.UTF_8) }
          ?.trim()
          ?.takeIf { it.isNotEmpty() }
          ?: "kmp_project"
        "$name.zip"
      }
      else -> projectURL.split("/").last()
    }
  }

  override fun getDescription(): String {
    return description
  }
}