// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import java.nio.file.Path

internal fun String.convertToPathOnTarget(target: TargetEnvironmentConfiguration?): String = Path.of(this).convertToPathOnTarget(target)

internal fun Path.convertToPathOnTarget(target: TargetEnvironmentConfiguration?): String {
  val mapper = target?.let { PythonInterpreterTargetEnvironmentFactory.getTargetWithMappedLocalVfs(it) }
  return mapper?.getTargetPath(this) ?: toString()
}

internal fun FullPathOnTarget.toLocalPathOn(target: TargetEnvironmentConfiguration?): Path {
  val mapper = target?.let { PythonInterpreterTargetEnvironmentFactory.getTargetWithMappedLocalVfs(it) }
  return mapper?.getLocalPath(this) ?: Path.of(this)
}

internal fun String.virtualFileOnTarget(target: TargetEnvironmentConfiguration? = null): VirtualFile? {
  if (target == null) return StandardFileSystems.local().findFileByPath(this)
  val path = Path.of(this)
  val mapper = PythonInterpreterTargetEnvironmentFactory.getTargetWithMappedLocalVfs(target) ?: return null
  return mapper.getVfsFromTargetPath(mapper.getTargetPath(path)!!)
}