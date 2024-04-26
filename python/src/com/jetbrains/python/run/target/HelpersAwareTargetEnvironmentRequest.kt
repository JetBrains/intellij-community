// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run.target

import com.intellij.execution.Platform
import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.value.TargetEnvironmentFunction
import com.intellij.execution.target.value.getRelativeTargetPath
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PythonHelpersLocator
import java.nio.file.Path
import kotlin.io.path.absolutePathString

data class PathMapping(val localPath: Path, val targetPathFun: TargetEnvironmentFunction<FullPathOnTarget>)

data class PythonHelpersMappings(val communityHelpers: PathMapping, val proHelpers: PathMapping?)

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

fun getPythonHelpers(): Path = PythonHelpersLocator.getHelpersRoot().toPath()

fun getPythonProHelpers(): Path? = if (PythonHelpersLocator.hasHelpersPro()) PythonHelpersLocator.getHelpersProRoot() else null

/**
 * Returns the mappings where Python helpers of Community and Professional versions are mapped to a single directory.
 * For example, when their contents are uploaded to the same directory on the SSH machine.
 */
fun singleDirectoryPythonHelpersMappings(targetPathFun: TargetEnvironmentFunction<FullPathOnTarget>): PythonHelpersMappings =
  PythonHelpersMappings(
    communityHelpers = getPythonHelpers() to targetPathFun,
    proHelpers = getPythonProHelpers()?.let { pythonProHelpers -> pythonProHelpers to targetPathFun },
  )

infix fun Path.to(targetPathFun: TargetEnvironmentFunction<FullPathOnTarget>): PathMapping =
  PathMapping(localPath = this, targetPathFun)

fun String.tryResolveAsPythonHelperDir(mappings: PythonHelpersMappings): PathMapping? {
  val (communityHelpers, proHelpers) = mappings
  val thisLocalPath = Path.of(this)
  return listOfNotNull(communityHelpers, proHelpers)
    .filter { (localPath) -> FileUtil.isAncestor(localPath.absolutePathString(), this, false) }
    .firstNotNullOfOrNull { (localPath, targetPathFun) ->
      FileUtil.getRelativePath(localPath.absolutePathString(), this, Platform.current().fileSeparator)?.let { relativePath ->
        thisLocalPath to targetPathFun.getRelativeTargetPath(relativePath)
      }
    }
}
