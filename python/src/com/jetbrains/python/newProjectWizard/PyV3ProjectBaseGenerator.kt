// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard

import com.intellij.facet.ui.ValidationResult
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.DirectoryProjectGenerator
import com.intellij.platform.ProjectGeneratorPeer
import com.intellij.util.SystemProperties
import com.jetbrains.python.Result
import com.jetbrains.python.newProjectWizard.impl.PyV3GeneratorPeer
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import java.nio.file.Path

/**
 * Extend this class to register a new project generator.
 * [typeSpecificSettings] are settings defaults.
 * [typeSpecificUI] is a UI to display these settings and bind then using Kotlin DSL UI
 * [allowedInterpreterTypes] limits a list of allowed interpreters (all interpreters are allowed by default)
 */
abstract class PyV3ProjectBaseGenerator<TYPE_SPECIFIC_SETTINGS : PyV3ProjectTypeSpecificSettings>(
  private val typeSpecificSettings: TYPE_SPECIFIC_SETTINGS,
  private val typeSpecificUI: PyV3ProjectTypeSpecificUI<TYPE_SPECIFIC_SETTINGS>?,
  private val allowedInterpreterTypes: Set<PythonInterpreterSelectionMode>? = null,
) : DirectoryProjectGenerator<PyV3BaseProjectSettings> {
  private val baseSettings = PyV3BaseProjectSettings()
  private val projectPathFlow = MutableStateFlow(Path.of(SystemProperties.getUserHome()))

  override fun generateProject(project: Project, baseDir: VirtualFile, settings: PyV3BaseProjectSettings, module: Module) {
    val coroutineScope = project.service<MyService>().coroutineScope
    coroutineScope.launch {
      typeSpecificSettings.generateProject(module, baseDir, settings.generateAndGetSdk(module, baseDir))
    }
  }

  override fun createPeer(): ProjectGeneratorPeer<PyV3BaseProjectSettings> =
    PyV3GeneratorPeer(baseSettings, projectPathFlow, typeSpecificUI?.let { Pair(it, typeSpecificSettings) }, allowedInterpreterTypes)

  override fun validate(baseDirPath: String): ValidationResult =
    when (val pathOrError = validateProjectPathAndGetPath(baseDirPath)) {
      is Result.Success<Path, *> -> {
        projectPathFlow.value = pathOrError.result
        ValidationResult.OK
      }
      is Result.Failure<*, @Nls String> -> ValidationResult(pathOrError.error)
    }


  @Service(Service.Level.PROJECT)
  private class MyService(val coroutineScope: CoroutineScope)
}