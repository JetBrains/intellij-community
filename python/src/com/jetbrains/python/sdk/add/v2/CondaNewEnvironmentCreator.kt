// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.add.target.conda.condaSupportedLanguages
import com.jetbrains.python.sdk.add.target.conda.createCondaSdkAlongWithNewEnv
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest
import kotlinx.coroutines.Dispatchers
import java.io.File

class CondaNewEnvironmentCreator(presenter: PythonAddInterpreterPresenter) : PythonAddEnvironment(presenter) {

  private val envName = propertyGraph.property("")
  private lateinit var pythonVersion: ObservableMutableProperty<LanguageLevel>
  private lateinit var versionComboBox: ComboBox<LanguageLevel>

  override fun buildOptions(panel: Panel) {
    with(panel) {
      row(message("sdk.create.python.version")) {
        pythonVersion = propertyGraph.property(condaSupportedLanguages.first())
        versionComboBox = comboBox(condaSupportedLanguages, textListCellRenderer { it!!.toPythonVersion() })
          .bindItem(pythonVersion)
          .component
      }
      row(message("sdk.create.custom.conda.env.name")) {
        textField()
          .bindText(envName)
      }
      row(message("sdk.create.conda.executable.path")) {
        textFieldWithBrowseButton(message("sdk.create.custom.conda.browse.title"),
                                  null,
                                  FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor())
          .align(Align.FILL)
          .bindText(state.condaExecutable)
          .displayLoaderWhen(presenter.detectingCondaExecutable, scope = presenter.scope, uiContext = presenter.uiContext)
      }
    }
  }

  override fun onShown() {
    envName.set(state.projectPath.get().substringAfterLast(File.separator))
  }

  override fun getOrCreateSdk(): Sdk {
    return runWithModalProgressBlocking(ModalTaskOwner.guess(), message("sdk.create.custom.conda.create.progress"),
                                        TaskCancellation.nonCancellable()) {
      presenter.createCondaCommand()
        .createCondaSdkAlongWithNewEnv(NewCondaEnvRequest.EmptyNamedEnv(pythonVersion.get(), envName.get()),
                                       Dispatchers.EDT,
                                       state.basePythonSdks.get(),
                                       ProjectManager.getInstance().defaultProject).getOrThrow()
    }
  }

}