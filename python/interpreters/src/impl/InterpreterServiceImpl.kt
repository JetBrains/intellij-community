// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.interpreters.impl

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.python.advancedApi.ExecutablePython
import com.intellij.python.community.execService.python.advancedApi.validatePythonAndGetVersion
import com.intellij.python.community.interpreters.Interpreter
import com.intellij.python.community.interpreters.InterpreterService
import com.intellij.python.community.interpreters.impl.PyInterpreterBundle.message
import com.intellij.python.community.interpreters.spi.InterpreterProvider
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.Path

internal object InterpreterServiceImpl : InterpreterService {
  private val logger = fileLogger()
  override suspend fun getInterpreters(projectDir: Path): List<Interpreter> {

    val sdkAndDatas = PythonSdkUtil.getAllSdks()
      .map { Pair(it.getOrCreateAdditionalData(), it) }
      .filter { (data, _) -> sdkApplicableToThePath(data, projectDir) }


    val result = mutableListOf<Interpreter>()
    for ((additionalData, sdk) in sdkAndDatas) {
      val interpreter = findInterpreter(additionalData, sdk)
      result.add(interpreter)
    }
    return result
  }

  override suspend fun getForModule(module: Module): Interpreter? {
    val sdk = ModuleRootManager.getInstance(module).sdk ?: return null
    if (sdk.sdkAdditionalData !is PythonSdkAdditionalData) {
      return null
    }
    return findInterpreter(sdk.getOrCreateAdditionalData(), sdk)
  }

  private suspend fun findInterpreter(
    additionalData: PythonSdkAdditionalData,
    sdk: Sdk,
  ): Interpreter {
    val flavorData = additionalData.flavorAndData.data
    val provider = InterpreterProvider.providerForData(flavorData)
    val interpreter = if (provider == null) {
      InvalidInterpreterImpl(SdkMixin(sdk, additionalData), message("py.unknown.type", flavorData.javaClass))
    }
    else {
      createInterpreter(provider, sdk, additionalData, flavorData)
    }
    return interpreter
  }


  private fun sdkApplicableToThePath(data: PythonSdkAdditionalData, projectDir: Path): Boolean {
    val associatedPathStr = data.associatedModulePath ?: return true
    val associatedPath = try {
      Path(associatedPathStr)
    }
    catch (e: InvalidPathException) {
      logger.warn("Skipping broken sdk associated with $associatedPathStr", e)
      return false
    }
    return projectDir.startsWith(associatedPath)
  }
}

private suspend fun <T : PyFlavorData> createInterpreter(provider: InterpreterProvider<T>, sdk: Sdk, additionalData: PythonSdkAdditionalData, flavorData: T): Interpreter {
  val homePath = try {
    Path(sdk.homePath!!)
  }
  catch (e: InvalidPathException) {
    return InvalidInterpreterImpl(SdkMixin(sdk, additionalData), message("py.interpreter.broken", sdk.homePath!!, e.localizedMessage))
  }
  when (val r = provider.createExecutablePython(homePath, flavorData)) {
    is Result.Failure -> {
      return InvalidInterpreterImpl(SdkMixin(sdk, additionalData), r.error.message)
    }
    is Result.Success -> {
      val executablePython = r.result
      val languageLevel = sdk.versionString?.let { LanguageLevel.fromPythonVersionSafe(it) }
                          ?: ExecService().validatePythonAndGetVersion(executablePython).getOr {
                            return InvalidInterpreterImpl(SdkMixin(sdk, additionalData), message("py.interpreter.no.version", it.error.message))
                          }
      return ValidInterpreterImpl(languageLevel, executablePython, SdkMixin(sdk, additionalData), provider.ui)
    }
  }
}

private class VanillaInterpreterProvider : InterpreterProvider<PyFlavorData.Empty> {
  override val ui: PyToolUIInfo? = null
  override val flavorDataClass: Class<PyFlavorData.Empty> = PyFlavorData.Empty::class.java

  override suspend fun createExecutablePython(sdkHomePath: Path, flavorData: PyFlavorData.Empty): Result<ExecutablePython, MessageError> {
    return Result.success(ExecutablePython.vanillaExecutablePython(sdkHomePath))
  }
}