// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.layout.predicate
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.sdk.add.target.conda.createCondaSdkFromExistingEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlin.time.Duration.Companion.seconds

@OptIn(FlowPreview::class)
class CondaExistingEnvironmentSelector(presenter: PythonAddInterpreterPresenter) : PythonAddEnvironment(presenter) {
  private lateinit var envComboBox: ComboBox<PyCondaEnv?>
  private lateinit var condaExecutableComboBox: TextFieldWithBrowseButton
  private val selectedEnvironment = propertyGraph.property<PyCondaEnv?>(null)
  private val lastLoadedConda = propertyGraph.property("")
  private val loadingCondaEnvironments = MutableStateFlow(value = false)

  override fun buildOptions(panel: Panel) {
    with(panel) {
      row(message("sdk.create.conda.executable.path")) {
        condaExecutableComboBox = textFieldWithBrowseButton()
          .bindText(state.condaExecutable)
          .displayLoaderWhen(presenter.detectingCondaExecutable, scope = presenter.scope, uiContext = presenter.uiContext)
          .align(Align.FILL)
          .component
      }

      row(message("sdk.create.custom.env.creation.type")) {
        val condaEnvironmentsLoaded = loadingCondaEnvironments.predicate(presenter.scope) { !it }

        envComboBox = comboBox(emptyList(), CondaEnvComboBoxListCellRenderer())
          .bindItem(selectedEnvironment)
          .displayLoaderWhen(loadingCondaEnvironments, makeTemporaryEditable = true,
                             scope = presenter.scope, uiContext = presenter.uiContext)
          .component

        link(message("sdk.create.custom.conda.refresh.envs"), action = { onReloadCondaEnvironments() })
          .visibleIf(condaEnvironmentsLoaded)
      }
    }
  }

  private fun onReloadCondaEnvironments() {
    val modalityState = ModalityState.current().asContextElement()
    state.scope.launch(Dispatchers.EDT + modalityState) {
      reloadCondaEnvironments(presenter.condaExecutableOnTarget)
    }
  }

  private suspend fun reloadCondaEnvironments(condaExecutableOnTarget: FullPathOnTarget) {
    try {
      loadingCondaEnvironments.value = true
      val commandExecutor = presenter.createExecutor()
      val environments = PyCondaEnv.getEnvs(commandExecutor, condaExecutableOnTarget)
      envComboBox.removeAllItems()
      val envs = environments.getOrLogException(LOG) ?: emptyList()
      selectedEnvironment.set(envs.firstOrNull())
      envs.forEach(envComboBox::addItem)
      lastLoadedConda.set(state.condaExecutable.get())
    }
    finally {
      loadingCondaEnvironments.value = false
    }
  }

  override fun onShown() {
    val modalityState = ModalityState.current().asContextElement()
    state.scope.launch(start = CoroutineStart.UNDISPATCHED) {
      presenter.currentCondaExecutableFlow
        .debounce(1.seconds)
        .collectLatest { condaExecutablePath ->
          withContext(Dispatchers.EDT + modalityState) {
            val pathOnTarget = condaExecutablePath?.let { presenter.getPathOnTarget(it) }
            if (pathOnTarget != null) {
              reloadCondaEnvironments(pathOnTarget)
            }
            else {
              loadingCondaEnvironments.value = false
            }
          }
        }
    }

    state.scope.launch(start = CoroutineStart.UNDISPATCHED) {
      presenter.currentCondaExecutableFlow.collectLatest {
        loadingCondaEnvironments.value = true
      }
    }

    state.scope.launch(start = CoroutineStart.UNDISPATCHED) {
      presenter.detectingCondaExecutable.collectLatest { isDetecting ->
        if (isDetecting) loadingCondaEnvironments.value = true
      }
    }
  }

  override fun getOrCreateSdk(): Sdk {
    return runWithModalProgressBlocking(ModalTaskOwner.guess(), message("sdk.create.custom.conda.select.progress"),
                                        TaskCancellation.nonCancellable()) {
      presenter.createCondaCommand()
        .createCondaSdkFromExistingEnv(selectedEnvironment.get()!!.envIdentity, state.basePythonSdks.get(),
                                       ProjectManager.getInstance().defaultProject)
    }
  }

  companion object {
    private val LOG = logger<CondaExistingEnvironmentSelector>()
  }
}