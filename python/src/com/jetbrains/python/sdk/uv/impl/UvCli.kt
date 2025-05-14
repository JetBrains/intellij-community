// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv.impl

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.SystemProperties
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.PyExecResult
import com.jetbrains.python.pathValidation.PlatformAndRoot
import com.jetbrains.python.pathValidation.ValidationRequest
import com.jetbrains.python.pathValidation.validateExecutableFile
import com.jetbrains.python.sdk.runExecutableWithProgress
import com.jetbrains.python.sdk.uv.UvCli
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.pathString

private const val UV_PATH_SETTING: String = "PyCharm.Uv.Path"

private var PropertiesComponent.uvPath: Path?
  get() {
    return getValue(UV_PATH_SETTING)?.let { Path.of(it) }
  }
  set(value) {
    setValue(UV_PATH_SETTING, value.toString())
  }

private fun validateUvExecutable(uvPath: Path?): ValidationInfo? {
  return validateExecutableFile(ValidationRequest(
    path = uvPath?.pathString,
    fieldIsEmpty = PyBundle.message("python.sdk.uv.executable.not.found"),
    // FIXME: support targets
    platformAndRoot = PlatformAndRoot.local
  ))
}

private suspend fun runUv(uv: Path, workingDir: Path, vararg args: String): PyExecResult<String> {
  return runExecutableWithProgress(uv, workingDir, *args)
}

private class UvCliImpl(val dispatcher: CoroutineDispatcher, uvPath: Path?) : UvCli {
  val uv: Path

  init {
    val path = uvPath ?: getUvExecutable()
    val error = validateUvExecutable(path)
    if (error != null) {
      throw RuntimeException(error.message)
    }

    uv = path!!
  }

  override suspend fun runUv(workingDir: Path, vararg args: String): PyExecResult<String> {
    return withContext(dispatcher) {
      runUv(uv, workingDir, *args)
    }
  }
}

fun detectUvExecutable(): Path? {
  val name = when {
    SystemInfo.isWindows -> "uv.exe"
    else -> "uv"
  }

  val binary = PathEnvironmentVariableUtil.findInPath(name)?.toPath()
  if (binary != null) {
    return binary
  }

  val userHome = SystemProperties.getUserHome()
  val appData = if (SystemInfo.isWindows) System.getenv("APPDATA") else null
  val paths = mutableListOf<Path>().apply {
    add(Path.of(userHome, ".local", "bin", name))
    add(Path.of(userHome, ".local", "bin", name))
    if (appData != null) {
      add(Path.of(appData, "Python", "Scripts", name))
    }
  }

  return paths.firstOrNull { it.exists() }
}

fun getUvExecutable(): Path? {
  return PropertiesComponent.getInstance().uvPath?.takeIf { it.exists() } ?: detectUvExecutable()
}

fun setUvExecutable(path: Path) {
  PropertiesComponent.getInstance().uvPath = path
}

fun hasUvExecutable(): Boolean {
  return getUvExecutable() != null
}

fun createUvCli(uv: Path? = null, dispatcher: CoroutineDispatcher = Dispatchers.IO): UvCli {
  return UvCliImpl(dispatcher, uv)
}