// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.testFramework

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.refreshAndFindVirtualFileOrDirectory
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively

fun mcpServerProjectFixture(testData: Path? = null): TestFixture<Project> {
  val baseProjectFixture = projectFixture(
    pathFixture = mcpServerProjectPathFixture(testData),
    openProjectTask = OpenProjectTask { createModule = false },
    openAfterCreation = true,
  )
  return testFixture {
    val project = baseProjectFixture.init()
    project.basePath?.let { Path.of(it).refreshAndFindVirtualFileOrDirectory()?.refresh(false, true) }
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    initialized(project) { }
  }
}

@OptIn(ExperimentalPathApi::class)
private fun mcpServerProjectPathFixture(testData: Path?): TestFixture<Path> = testFixture {
  val path = tempPathFixture().init()
  testData?.let { resolveRelativelyToRoot(it).copyToRecursively(path, followLinks = false, overwrite = true) }
  initialized(path) { }
}

private fun resolveRelativelyToRoot(path: Path): Path {
  if (path.isAbsolute) return path

  val repoRoot = PathManager.getHomeDirFor(RepositoryRootAnchor::class.java)
                 ?: error("Cannot resolve repository root")
  return repoRoot.resolve(path)
}

/** Anchor whose code-source location is used to locate the repository root for relative test-data paths. */
private class RepositoryRootAnchor
