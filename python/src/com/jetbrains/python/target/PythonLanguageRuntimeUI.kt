// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.target

import com.intellij.execution.target.CustomToolLanguageConfigurable
import com.intellij.execution.target.LanguageRuntimeType
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.getTargetType
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.validation.WHEN_PROPERTY_CHANGED
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.launchOnShow
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.TraceContext
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.emit
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathFlows
import com.jetbrains.python.onFailure
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.add.collector.PythonNewInterpreterAddedCollector
import com.jetbrains.python.sdk.add.v2.*
import com.jetbrains.python.sdk.configurePythonSdk
import com.jetbrains.python.sdk.service.PySdkService.Companion.pySdkService
import com.jetbrains.python.util.ShowingMessageErrorSync
import kotlinx.coroutines.supervisorScope
import java.awt.Dimension
import java.nio.file.Path
import java.util.function.Supplier

class PythonLanguageRuntimeUI(
  val project: Project,
  val config: PythonLanguageRuntimeConfiguration,
  val targetSupplier: Supplier<TargetEnvironmentConfiguration>,
) : BoundConfigurable(message("configurable.name.python.language")), CustomToolLanguageConfigurable<Sdk> {
  private val module: Module = project.modules.first() // TODO get the real one, this behaviour was copied from legacy "createSdkForTarget"
  private var introspectable: LanguageRuntimeType.Introspectable? = null
  private var stateChangedCallback: (() -> Unit)? = null

  private lateinit var mainPanel: PythonAddCustomInterpreter<PathHolder.Target>
  private var validationErrors: Collection<ValidationInfo> = emptyList()
  private val errorSink: ErrorSink = ShowingMessageErrorSync

  override fun createPanel(): DialogPanel {
    val targetEnvironmentConfiguration = targetSupplier.get()
    val model = PythonLocalAddInterpreterModel(
      ProjectPathFlows.create(Path.of(project.basePath!!)),
      FileSystem.Target(
        targetEnvironmentConfiguration = targetEnvironmentConfiguration,
        pythonLanguageRuntimeConfiguration = config,
      )
    )
    model.navigator.selectionMode = AtomicProperty(PythonInterpreterSelectionMode.CUSTOM)

    mainPanel = PythonAddCustomInterpreter(
      model = model,
      module = module,
      errorSink = ShowingMessageErrorSync,
      limitExistingEnvironments = true,
    )

    val dialogPanel = panel {
      mainPanel.setupUI(this, WHEN_PROPERTY_CHANGED(AtomicProperty(false)))
    }.apply {
      minimumSize = Dimension(800, 400)
    }

    dialogPanel.launchOnShow(
      debugName = "PythonLanguageRuntimeUI launchOnShow",
      context = TraceContext(
        title = message("tracecontext.add.remote.python.sdk.dialog", targetEnvironmentConfiguration.getTargetType().displayName),
        parentTraceContext = null
      )
    ) {
      supervisorScope {
        model.initialize(this@supervisorScope)
        mainPanel.onShown(this@supervisorScope)
      }
    }

    disposable?.let {
      dialogPanel.registerValidators(it) { map ->
        this.validationErrors = map.values
        this.stateChangedCallback?.invoke()
      }
    }

    return dialogPanel
  }

  override fun setIntrospectable(introspectable: LanguageRuntimeType.Introspectable) {
    this.introspectable = introspectable
  }

  override fun registerStateChangedCallback(stateChangedCallback: () -> Unit) {
    this@PythonLanguageRuntimeUI.stateChangedCallback = stateChangedCallback
  }

  @RequiresEdt
  override fun createCustomTool(): Sdk? {
    val sdkManager = mainPanel.currentSdkManager

    val sdk = runWithModalProgressBlocking(project, message("python.sdk.progress.setting.up.environment")) {
      val sdk = sdkManager.getOrCreateSdkWithModal(ModuleOrProject.ModuleAndProject(module)).onFailure {
        errorSink.emit(it)
      }.successOrNull

      sdk?.let {
        configurePythonSdk(project, module, it)
        project.pySdkService.persistSdk(it)
        PythonNewInterpreterAddedCollector.logPythonNewInterpreterAdded(it, false)
      }

      sdk
    }


    return sdk
  }

  override fun validate(): Collection<ValidationInfo> = validationErrors
}