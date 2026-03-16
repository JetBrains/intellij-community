package com.intellij.ide.starter.project

import com.intellij.ide.starter.ide.IDETestContext
import java.nio.file.Path
import kotlin.io.path.notExists
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Project, that somehow already exist on filesystem.
 * So we cannot link it with any particular URL
 *
 * See [ReusableLocalProjectInfo]
 */
data class LocalProjectInfo(
  val projectDir: Path,
  override val isReusable: Boolean = false,
  override val downloadTimeout: Duration = 1.minutes,
  override val configureProjectBeforeUse: (IDETestContext) -> Unit = {},
  private val description: String = ""
) : ProjectInfoSpec {
  override fun downloadAndUnpackProject(): Path? {
    if (projectDir.notExists()) {
      return null
    }

    return projectDir.toAbsolutePath()
  }

  override fun getDescription(): String {
    return description
  }
}