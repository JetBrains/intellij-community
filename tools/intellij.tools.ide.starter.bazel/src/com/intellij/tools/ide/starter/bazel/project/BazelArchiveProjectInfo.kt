// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.bazel.project

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.project.ProjectInfoSpec
import com.intellij.ide.starter.utils.FileSystem
import com.intellij.ide.starter.utils.FileSystem.isDirUpToDate
import com.intellij.testFramework.common.BazelTestUtil
import com.intellij.testFramework.common.bazel.BazelTestDependencyHttpFileDownloader
import org.jetbrains.intellij.bazelEnvironment.BazelLabel
import org.kodein.di.instance
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class BazelArchiveProjectInfo(
  val bazelLabel: String,
  val testDependencyLoader: StarterBazelTestDependencyDownloader,
  val projectHomeRelativePath: (Path) -> Path = { it },
  override val isReusable: Boolean = true,
  override val downloadTimeout: Duration = 10.minutes,
  override val configureProjectBeforeUse: (IDETestContext) -> Unit = {},
  private val description: String = "",
) : ProjectInfoSpec {

  @OptIn(ExperimentalPathApi::class)
  override fun downloadAndUnpackProject(): Path {
    val label = BazelLabel.fromString(bazelLabel)
    val archiveFile = testDependencyLoader.getDepsByLabel(label)

    val targetName = label.target
      .removeSuffix(".zip")
      .removeSuffix(".tar.gz")
      .replace(Regex("[^a-zA-Z0-9._-]"), "_")

    val extractionDir = if (BazelTestUtil.isUnderBazelTest) {
      BazelTestUtil.bazelTestTmpDirPath.resolve(targetName)
    }
    else {
      val globalPaths by di.instance<GlobalPaths>()
      globalPaths.cacheDirForProjects.resolve("bazel-archives").resolve(targetName)
    }

    val needsExtraction = !isReusable || !extractionDir.exists() || !extractionDir.isDirUpToDate()
    if (needsExtraction) {
      if (extractionDir.exists()) {
        extractionDir.deleteRecursively()
      }
      extractionDir.createDirectories()
      FileSystem.unpack(archiveFile, extractionDir)
    }

    return extractionDir.let(projectHomeRelativePath)
  }

  override fun getDescription(): String {
    return description
  }
}
