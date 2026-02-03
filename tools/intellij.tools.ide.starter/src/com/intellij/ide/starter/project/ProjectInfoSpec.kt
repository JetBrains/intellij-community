package com.intellij.ide.starter.project

import com.intellij.ide.starter.ide.IDETestContext
import java.nio.file.Path
import kotlin.time.Duration

interface ProjectInfoSpec {
  /**
   * "true" - the same project data can be shared between tests
   * "false" - before each test the project will be removed and unpacked from scratch/cleaned (depends on the implementation of [ProjectInfoSpec]).
   * */
  val isReusable: Boolean
  val downloadTimeout: Duration

  fun downloadAndUnpackProject(): Path?

  /**
   * Use this to tune/configure the project before IDE start
   */
  val configureProjectBeforeUse: (IDETestContext) -> Unit

  fun getDescription(): String = ""
}
