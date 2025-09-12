// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.xmlb.XmlSerializer
import com.jetbrains.python.PyBundle
import com.jetbrains.python.Result
import com.jetbrains.python.onFailure
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.associatedModulePath
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.sdk.uv.UvSdkAdditionalData
import com.jetbrains.python.venvReader.tryResolvePath
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
enum class UvRunType {
  SCRIPT,
  MODULE,
}

@ApiStatus.Internal
data class UvRunConfigurationOptions(
  var runType: UvRunType = UvRunType.SCRIPT,
  var scriptOrModule: String = "",
  var args: List<String> = listOf(),
  var env: Map<String, String> = mapOf(),
  var checkSync: Boolean = true,
  var uvSdkKey: String? = null,
  var uvArgs: List<String> = listOf()
) {
  val uvSdk: Sdk?
    get() = uvSdkKey?.let {PythonSdkUtil.findSdkByKey(it)}

  val workingDirectory: Path?
    get() = (uvSdk?.sdkAdditionalData as? UvSdkAdditionalData)?.uvWorkingDirectory
      ?: tryResolvePath(uvSdk?.associatedModulePath)
}

@ApiStatus.Internal
class UvRunConfiguration(
  project: Project,
  factory: ConfigurationFactory,
) : AbstractPythonRunConfiguration<UvRunConfiguration>(project, factory) {
  var options: UvRunConfigurationOptions = UvRunConfigurationOptions(
    uvSdkKey = module?.pythonSdk?.name ?: project.pythonSdk?.name,
  )

  override fun createConfigurationEditor(): SettingsEditor<UvRunConfiguration> = UvRunSettingsEditor(project, this, uvSdkList())

  override fun getState(
    executor: Executor,
    environment: ExecutionEnvironment,
  ): RunProfileState? =
    UvRunConfigurationState(this, environment, project)

  override fun readExternal(element: Element) {
    readExternal(element, options)
  }

  override fun writeExternal(element: Element) {
    writeExternal(element, options)
  }

  override fun checkConfiguration() {
    checkConfiguration(options).onFailure {
      throw RuntimeConfigurationError(it)
    }
  }

  override fun getSdk(): Sdk? {
    return options.uvSdk
  }
}

@ApiStatus.Internal
fun readExternal(element: Element, options: UvRunConfigurationOptions) {
  XmlSerializer.deserializeInto(options, element)
}

@ApiStatus.Internal
fun writeExternal(element: Element, options: UvRunConfigurationOptions) {
  XmlSerializer.serializeInto(options, element)
}

@ApiStatus.Internal
fun checkConfiguration(options: UvRunConfigurationOptions): Result<Unit, @NlsSafe String> {
  if (options.uvSdk == null) {
    return Result.failure(PyBundle.message("uv.run.configuration.validation.sdk"))
  }

  val runType = options.runType
  val scriptOrModule = options.scriptOrModule

  if (runType == UvRunType.SCRIPT && (tryResolvePath(scriptOrModule) == null || scriptOrModule.isEmpty())) {
    return Result.failure(PyBundle.message("uv.run.configuration.validation.script"))
  }

  if (runType == UvRunType.MODULE && scriptOrModule.isEmpty()) {
    return Result.failure(PyBundle.message("uv.run.configuration.validation.module"))
  }

  return Result.success(Unit)
}