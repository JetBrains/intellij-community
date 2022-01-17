// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("PyTargetPositionConverters")

package com.jetbrains.python.debugger

import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.getLocalPaths
import com.intellij.execution.target.getTargetPaths
import com.intellij.openapi.util.io.FileUtil
import com.jetbrains.python.debugger.remote.vfs.PyRemotePositionConverter
import com.jetbrains.python.remote.PyRemotePathMapper

fun createTargetedPositionConverter(debugProcess: PyDebugProcess, targetEnvironment: TargetEnvironment): PyPositionConverter {
  val pathMapper = PyTargetPathMapper(targetEnvironment)
  return PyRemotePositionConverter(debugProcess, pathMapper)
}

private class PyTargetPathMapper(private val targetEnvironment: TargetEnvironment) : PyRemotePathMapper() {
  override fun convertToLocal(remotePath: String): String {
    return targetEnvironment.getLocalPaths(remotePath).firstOrNull() ?: super.convertToLocal(remotePath)
  }

  override fun convertToRemote(localPath: String): String {
    return targetEnvironment.getTargetPaths(FileUtil.toSystemDependentName(localPath)).firstOrNull() ?: super.convertToRemote(localPath)
  }

  override fun isEmpty(): Boolean {
    return false
  }
}