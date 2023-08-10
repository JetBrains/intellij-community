// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.project.test.base

import org.jetbrains.kotlin.idea.performance.tests.utils.project.ProjectOpenAction
import java.nio.file.Path
import java.nio.file.Paths

data class ProjectData(
  val id: String,
  val path: Path,
  val openAction: ProjectOpenAction,
)

fun perfTestProjectPath(relativePath: String): Path {
    val dir = System.getenv("PERF_TESTDATA_DIR")
        ?: error("Please specify environment variable `PERF_TESTDATA_DIR` pointing to the directory with performance testdata")
    return Paths.get(dir).resolve(relativePath)
}