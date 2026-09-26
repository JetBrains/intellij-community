package com.intellij.ide.starter.project

import com.intellij.ide.starter.ide.IDETestContext
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * To open IDE with the absence of a project
 */
object NoProject : ProjectInfoSpec {
  override val isReusable: Boolean = true
  override val downloadTimeout: Duration = 0.seconds

  override fun downloadAndUnpackProject(): Path? = null

  override val configureProjectBeforeUse: (IDETestContext) -> Unit = {}
}