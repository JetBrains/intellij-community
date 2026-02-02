package com.intellij.ide.starter.project

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.utils.FileSystem.deleteRecursivelyQuietly
import com.intellij.tools.ide.util.common.logOutput
import org.kodein.di.instance
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.name
import kotlin.io.path.notExists
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Represents information for a project located on the machine.
 *
 * Creates a copy of the project, so any changes in the project will not affect other tests
 * Could be used for local development.
 */
class ReusableLocalProjectInfo(
  val projectDir: Path,
  override val downloadTimeout: Duration = 1.minutes,
  override val configureProjectBeforeUse: (IDETestContext) -> Unit = {},
  private val description: String = "",
) : ProjectInfoSpec {
  override val isReusable: Boolean = false

  @OptIn(ExperimentalPathApi::class)
  override fun downloadAndUnpackProject(): Path? {
    val globalPaths by di.instance<GlobalPaths>()

    if (projectDir.notExists()) {
      return null
    }

    val projectsUnpacked = globalPaths.cacheDirForProjects.resolve("unpacked").createDirectories()
    val projectHome = projectsUnpacked / projectDir.last().name

    val isDeleted = projectHome.deleteRecursivelyQuietly()
    if (!isDeleted) {
      logOutput("Failed to delete $projectHome")
    }
    val newPath = projectDir.copyToRecursively(projectHome, followLinks = false, overwrite = true)

    return newPath.toAbsolutePath()
  }

  override fun getDescription(): String {
    return description
  }
}
