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
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.isRegularFile
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
  private val stripRoot: Boolean = true,
  private val unpackInplace: Boolean = false
) : ProjectInfoSpec {

  private fun getTopMostFoldersFromZip(zipFile: Path): List<String> = JBZipFile(zipFile, StandardCharsets.UTF_8, true, ThreeState.UNSURE).entries.map { it.name.substringBefore('/') }.distinct()

  @OptIn(ExperimentalPathApi::class)
  override fun downloadAndUnpackProject(): Path {
    val globalPaths by di.instance<GlobalPaths>()

    val projectsUnpacked = globalPaths.cacheDirForProjects.resolve("unpacked").createDirectories()

    val zipFile = globalPaths.cacheDirForProjects.resolve("zip").resolve(projectURL.transformUrlToZipName())

    val isDownloaded = try {
      HttpClient.downloadIfMissing(url = projectURL, targetFile = zipFile, timeout = downloadTimeout)
    }
    catch (e: Exception) {
      throw SetupException("Failed to download the project from $projectURL: ${e.message}", e)
    }
    if (!isDownloaded) {
      throw SetupException("Failed to download $zipFile from $projectURL: see the download failures logged above")
    }
    check(zipFile.isRegularFile()) { "Expected .zip file, got: $zipFile" }

    val targetDir = if (unpackInplace) {
      projectsUnpacked
    } else {
      projectsUnpacked.resolve("${zipFile.fileName}.d")
    }
    val projectHome = if (stripRoot) {
      val roots = getTopMostFoldersFromZip(zipFile)
        .filterNot { it == "__MACOSX" || it == "README.md" }  // ignore
      check(roots.size == 1) {
        "Expected exactly one top-level entry in .zip file to strip, got: ${roots.joinToString()}"
      }

      targetDir.resolve(roots.single())
    }
    else {
      targetDir
    }.let(projectHomeRelativePath)

    if (!isReusable) {
      val isDeleted = targetDir.deleteRecursivelyQuietly()
      if (!isDeleted) {
        logOutput("Failed to delete $targetDir")
      }
    }

    if (projectHome.isDirUpToDate()) {
      logOutput("Already unpacked project $projectHome will be used in the test")
      return projectHome
    }
    else {
      targetDir.deleteRecursivelyQuietly()
    }

    FileSystem.unpack(zipFile, targetDir)

    return projectHome
  }

  private fun String.transformUrlToZipName(): String {
    val spaceVcsArchiveMirrorPrefix = "https://packages.jetbrains.team/files/p/ij/space-vcs-archive-mirror/"
    return when {
      projectURL.contains("https://github.com") -> {
        this.removePrefix("https://github.com/").split("/").joinToString("_", postfix = ".zip")
      }
      projectURL.startsWith(spaceVcsArchiveMirrorPrefix) -> {
        this.removePrefix(spaceVcsArchiveMirrorPrefix).split("/").joinToString("_", postfix = ".zip")
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