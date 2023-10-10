// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.observable.util.not
import com.intellij.openapi.progress.ModalTaskOwner
import com.intellij.openapi.progress.TaskCancellation
import com.intellij.openapi.progress.runWithModalProgressBlocking
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindText
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.sdk.add.target.conda.TargetEnvironmentRequestCommandExecutor
import com.jetbrains.python.sdk.add.target.conda.createCondaSdkFromExistingEnv
import com.jetbrains.python.sdk.add.target.conda.suggestCondaPath
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CondaExistingEnvironmentSelector(state: PythonAddInterpreterState) : PythonAddEnvironment(state) {

  private lateinit var envComboBox: ComboBox<PyCondaEnv?>
  private lateinit var condaExecutableComboBox: TextFieldWithBrowseButton
  private var selectedEnvironment = propertyGraph.property<PyCondaEnv?>(null)
  private var lastLoadedConda = propertyGraph.property("")
  private var condaEnvironmentsLoaded = propertyGraph.property(true)


  override fun buildOptions(panel: Panel) {
    with(panel) {
      row(message("sdk.create.conda.executable.path")) {
        condaExecutableComboBox = textFieldWithBrowseButton()
          .bindText(state.condaExecutable)
          .align(Align.FILL)
          .component
      }

      row(message("sdk.create.custom.env.creation.type")) {
        envComboBox = comboBox<PyCondaEnv?>(emptyList(), CondaEnvComboBoxListCellRenderer())
          .bindItem(selectedEnvironment)
          .enabledIf(condaEnvironmentsLoaded)
          .component

        link(message("sdk.create.custom.conda.refresh.envs")) {
          val modalityState = ModalityState.current().asContextElement()
          state.scope.launch(Dispatchers.Default + modalityState) {
            val commandExecutor = TargetEnvironmentRequestCommandExecutor(LocalTargetEnvironmentRequest())
            val environments = PyCondaEnv.getEnvs(commandExecutor, state.condaExecutable.get())
            withContext(Dispatchers.Main + modalityState) {
              envComboBox.removeAllItems()
              val envs = environments.getOrThrow() // todo error handling
              selectedEnvironment.set(envs.first())
              envs.forEach(envComboBox::addItem)
              lastLoadedConda.set(state.condaExecutable.get())
            }
          }
        }.visibleIf(!condaEnvironmentsLoaded)
      }

    }
  }

  override fun onShown() {
    val modalityState = ModalityState.current().asContextElement()
    state.scope.launch(Dispatchers.Default + modalityState) {

      val pathOnTarget = suggestCondaPath()!! // todo flow for undiscovered conda
      val commandExecutor = TargetEnvironmentRequestCommandExecutor(LocalTargetEnvironmentRequest())
      val environments = PyCondaEnv.getEnvs(commandExecutor, pathOnTarget)

      withContext(Dispatchers.Main + modalityState) {

        lastLoadedConda.set(pathOnTarget)
        envComboBox.removeAllItems()
        val envs = environments.getOrThrow()
        selectedEnvironment.set(envs.first())
        envs.forEach(envComboBox::addItem)


        val refreshIcon = ExtendableTextComponent.Extension.create(AllIcons.Actions.Refresh, AllIcons.Actions.Refresh, "Refresh conda") {
          // todo decide whether we need icon or just link
        }
        val textComponent = condaExecutableComboBox.childComponent as ExtendableTextComponent

        condaEnvironmentsLoaded.dependsOn(state.condaExecutable, ::condasMatch)
        condaEnvironmentsLoaded.dependsOn(lastLoadedConda, ::condasMatch)

        condaEnvironmentsLoaded.afterChange {
          if (it) textComponent.removeExtension(refreshIcon)
          else textComponent.addExtension(refreshIcon)
        }


      }
    }
  }

  private fun condasMatch(): Boolean = state.condaExecutable.get() == lastLoadedConda.get()

  override fun getOrCreateSdk(): Sdk {
    return runWithModalProgressBlocking(ModalTaskOwner.guess(), message("sdk.create.custom.conda.select.progress"), TaskCancellation.nonCancellable()) {
      PyCondaCommand(state.condaExecutable.get(), null)
        .createCondaSdkFromExistingEnv(selectedEnvironment.get()!!.envIdentity, state.basePythonSdks.get(),
                                       ProjectManager.getInstance().defaultProject)
    }
  }

}