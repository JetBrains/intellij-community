// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv.impl

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.util.SystemProperties
import com.jetbrains.python.PyBundle
import com.jetbrains.python.pathValidation.PlatformAndRoot
import com.jetbrains.python.pathValidation.ValidationRequest
import com.jetbrains.python.pathValidation.validateExecutableFile
import com.jetbrains.python.sdk.runExecutable
import com.jetbrains.python.sdk.uv.UvCli
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

import java.nio.file.Path

import kotlin.io.path.exists
import kotlin.io.path.pathString

internal fun detectUvExecutable(): Path? {
  val name = "uv"
  return PathEnvironmentVariableUtil.findInPath(name)?.toPath() ?: SystemProperties.getUserHome().let { homePath ->
    Path.of(homePath, ".cargo", "bin", name).takeIf { it.exists() }
  }
}

internal fun validateUvExecutable(uvPath: Path?): ValidationInfo? {
  return validateExecutableFile(ValidationRequest(
    path = uvPath?.pathString,
    fieldIsEmpty = PyBundle.message("python.sdk.uv.executable.not.found"),
    // FIXME: support targets
    platformAndRoot = PlatformAndRoot.local
  ))
}

internal suspend fun runUv(uv: Path, workingDir: Path, vararg args: String): Result<String> {
  return runExecutable(uv, workingDir, *args)
}

internal class UvCliImpl(val dispatcher: CoroutineDispatcher, uvPath: Path?): UvCli {
  val uv: Path

  init {
    val path = uvPath ?: detectUvExecutable()
    val error = validateUvExecutable(path)
    if (error != null) {
      throw RuntimeException(error.message)
    }

    uv = path!!
  }

  override suspend fun runUv(workingDir: Path, vararg args: String): Result<String> {
    with(Dispatchers.IO) {
      return runUv(uv, workingDir, *args)
    }
  }
}

fun createUvCli(dispatcher: CoroutineDispatcher = Dispatchers.IO, uv: Path? = null): UvCli {
  return UvCliImpl(dispatcher, uv)
}