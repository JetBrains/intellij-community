// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.target

import com.intellij.execution.Platform
import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.value.TargetEnvironmentFunction
import com.intellij.execution.target.value.getRelativeTargetPath
import com.intellij.openapi.util.io.FileUtil
import com.intellij.python.community.helpersLocator.PythonHelpersLocator
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.nio.file.Path
import kotlin.io.path.absolutePathString

data class PathMapping(val localPath: Path, val targetPathFun: TargetEnvironmentFunction<FullPathOnTarget>)

data class PythonHelpersMappings(val helpers: List<PathMapping>)

/**
 * The target request for Python interpreter configured in PyCharm on a
 * specific target.
 */
interface HelpersAwareTargetEnvironmentRequest {
  val targetEnvironmentRequest: TargetEnvironmentRequest

  /**
   * The value that could be resolved to the path to the root of PyCharm
   * helpers scripts.
   */
  @RequiresBackgroundThread
  fun preparePyCharmHelpers(): PythonHelpersMappings
}

fun getPythonHelpers(): List<Path> = PythonHelpersLocator.getHelpersRoots()

/**
 * Returns the mappings where Python helpers of Community and Professional versions are mapped to a single directory.
 * For example, when their contents are uploaded to the same directory on the SSH machine.
 */
fun singleDirectoryPythonHelpersMappings(targetPathFun: TargetEnvironmentFunction<FullPathOnTarget>): PythonHelpersMappings =
  PythonHelpersMappings(getPythonHelpers().map { it to targetPathFun })


infix fun Path.to(targetPathFun: TargetEnvironmentFunction<FullPathOnTarget>): PathMapping =
  PathMapping(localPath = this, targetPathFun)

fun String.tryResolveAsPythonHelperDir(mappings: PythonHelpersMappings): PathMapping? {
  val thisLocalPath = Path.of(this)
  val rootPaths = mappings.helpers
    .filter { (localPath) -> FileUtil.isAncestor(localPath.absolutePathString(), this, false) }
  return rootPaths
    .firstNotNullOfOrNull { (localPath, targetPathFun) ->
      val relativePath = FileUtil.getRelativePath(localPath.absolutePathString(), this, Platform.current().fileSeparator)
      relativePath ?: return@firstNotNullOfOrNull null
      val relTargetPath = targetPathFun.getRelativeTargetPath(relativePath)
      thisLocalPath to relTargetPath

    }
}
