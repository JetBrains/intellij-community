// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard

import com.intellij.facet.ui.ValidationResult
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.DirectoryProjectGenerator
import com.intellij.platform.ProjectGeneratorPeer
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.Result
import com.jetbrains.python.newProjectWizard.collector.PyProjectTypeGenerator
import com.jetbrains.python.newProjectWizard.collector.PythonNewProjectWizardCollector.logPythonNewProjectGenerated
import com.jetbrains.python.newProjectWizard.impl.PyV3GeneratorPeer
import com.jetbrains.python.newProjectWizard.impl.PyV3UIServicesProd
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathFlows.Companion.validatePath
import com.jetbrains.python.onFailure
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMode
import com.jetbrains.python.statistics.version
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import kotlin.reflect.jvm.jvmName

/**
 * Extend this class to register a new project generator.
 * [typeSpecificSettings] are settings defaults.
 * [typeSpecificUI] is a UI to display these settings and bind then using Kotlin DSL UI
 * [allowedInterpreterTypes] limits a list of allowed interpreters (all interpreters are allowed by default)
 * [newProjectName] is a default name of the new project ([getName]Project is default)
 *
 * To test this class, see [setUiServices]
 */
abstract class PyV3ProjectBaseGenerator<TYPE_SPECIFIC_SETTINGS : PyV3ProjectTypeSpecificSettings>(
  private val typeSpecificSettings: TYPE_SPECIFIC_SETTINGS,
  private val typeSpecificUI: PyV3ProjectTypeSpecificUI<TYPE_SPECIFIC_SETTINGS>?,
  private val allowedInterpreterTypes: Set<PythonInterpreterSelectionMode>? = null,
  private val _newProjectName: @NlsSafe String? = null,
) : DirectoryProjectGenerator<PyV3BaseProjectSettings>, PyProjectTypeGenerator {
  private val baseSettings = PyV3BaseProjectSettings()
  private var uiServices: PyV3UIServices = PyV3UIServicesProd
  val newProjectName: @NlsSafe String get() = _newProjectName ?: "${name.replace(" ", "")}Project"

  override val projectTypeForStatistics: @NlsSafe String = this::class.jvmName

  /**
   * Run this method as before any other to substitute services with mock for tests
   */
  @TestOnly
  fun setUiServices(uiServices: PyV3UIServices) {
    this.uiServices = uiServices
  }


  @RequiresEdt
  override fun generateProject(project: Project, baseDir: VirtualFile, settings: PyV3BaseProjectSettings, module: Module) {
    val coroutineScope = project.service<MyService>().coroutineScope
    coroutineScope.launch {
      val (sdk, interpreterStatistics) = settings.generateAndGetSdk(module, baseDir).getOr {
        withContext(Dispatchers.EDT) {
          // TODO: Migrate to python Result using PyError as exception not to make this dynamic check
          uiServices.errorSink.emit(it.error)
        }
        return@launch // Since we failed to generate project, we do not need to go any further
      }

      withContext(Dispatchers.EDT) {
        writeAction {
          VirtualFileManager.getInstance().syncRefresh()
        }
      }

      val pythonVersion = withContext(Dispatchers.IO) { sdk.version }
      logPythonNewProjectGenerated(interpreterStatistics,
                                   pythonVersion,
                                   this@PyV3ProjectBaseGenerator,
                                   emptyList())

      // Project view must be expanded (PY-75909) but it can't be unless it contains some files.
      // Either base settings (which create venv) might generate some or type specific settings (like Django) may.
      // So we expand it right after SDK generation, but if there are no files yet, we do it again after project generation
      uiServices.expandProjectTreeView(project)
      typeSpecificSettings.generateProject(module, baseDir, sdk).onFailure {
        uiServices.errorSink.emit(it)
      }
      uiServices.expandProjectTreeView(project)
    }
  }


  override fun createPeer(): ProjectGeneratorPeer<PyV3BaseProjectSettings> =
    PyV3GeneratorPeer(baseSettings, typeSpecificUI?.let { Pair(it, typeSpecificSettings) }, allowedInterpreterTypes, uiServices)

  override fun validate(baseDirPath: String): ValidationResult =
    when (val pathOrError = validatePath(baseDirPath)) {
      is Result.Success -> {
        ValidationResult.OK
      }
      is Result.Failure -> ValidationResult(pathOrError.error)
    }


  @Service(Service.Level.PROJECT)
  private class MyService(val coroutineScope: CoroutineScope)
}
