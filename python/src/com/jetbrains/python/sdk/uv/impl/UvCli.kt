// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv.impl

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.localEel
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.pathValidation.PlatformAndRoot
import com.jetbrains.python.pathValidation.ValidationRequest
import com.jetbrains.python.pathValidation.validateExecutableFile
import com.jetbrains.python.sdk.detectTool
import com.jetbrains.python.sdk.runExecutableWithProgress
import com.jetbrains.python.sdk.uv.UvCli
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.minutes

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

private suspend fun runUv(uv: Path, workingDir: Path, vararg args: String): PyResult<String> {
  return runExecutableWithProgress(uv, workingDir,
                                   env = mapOf("VIRTUAL_ENV" to ".venv"), timeout = 10.minutes, args = args)
}

private class UvCliImpl(val dispatcher: CoroutineDispatcher, val uv: Path) : UvCli {

  override suspend fun runUv(workingDir: Path, vararg args: String): PyResult<String> {
    return withContext(dispatcher) {
      runUv(uv, workingDir, *args)
    }
  }
}

suspend fun detectUvExecutable(eel: EelApi): Path? = detectTool("uv", eel)

suspend fun getUvExecutable(eel: EelApi = localEel): Path? {
  return PropertiesComponent.getInstance().uvPath?.takeIf { it.exists() } ?: detectUvExecutable(eel)
}

fun setUvExecutable(path: Path) {
  PropertiesComponent.getInstance().uvPath = path
}

suspend fun hasUvExecutable(): Boolean {
  return getUvExecutable() != null
}

suspend fun createUvCli(uv: Path? = null, dispatcher: CoroutineDispatcher = Dispatchers.IO): PyResult<UvCli> {
  val path = uv ?: getUvExecutable()
  val error = validateUvExecutable(path)
  return if (error != null) {
    PyResult.localizedError(error.message)
  }
  else PyResult.success(UvCliImpl(dispatcher, path!!))
}