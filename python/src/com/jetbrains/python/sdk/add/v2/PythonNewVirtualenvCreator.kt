// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.dsl.builder.*
import com.intellij.util.PathUtil
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.sdk.PySdkSettings
import com.jetbrains.python.sdk.add.LocalContext
import com.jetbrains.python.sdk.add.ProjectLocationContext
import com.jetbrains.python.sdk.configuration.createVirtualEnvSynchronously
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

class PythonNewVirtualenvCreator(presenter: PythonAddInterpreterPresenter) : PythonAddEnvironment(presenter) {
  private val location = propertyGraph.property("")
  private val inheritSitePackages = propertyGraph.property(false)
  private val makeAvailable = propertyGraph.property(false)
  private lateinit var versionComboBox: ComboBox<String>
  private var locationModified = false

  override fun buildOptions(panel: Panel) {
    with(panel) {
      row(message("sdk.create.custom.base.python")) {
        versionComboBox = pythonBaseInterpreterComboBox(presenter, presenter.basePythonSdksFlow, presenter.basePythonHomePath)
      }
      row(message("sdk.create.custom.location")) {
        textFieldWithBrowseButton(message("sdk.create.custom.venv.location.browse.title"),
                                  fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor())
          .bindText(location)
          .whenTextChangedFromUi { locationModified = true }
          .cellValidation {
            addInputRule("This directory already exists") {
              Paths.get(it.text).exists() // todo validation with custom component, once available
            }
          }
          .align(Align.FILL)
      }

      row("") {
        checkBox(message("sdk.create.custom.inherit.packages"))
          .bindSelected(inheritSitePackages)
      }
      row("") {
        checkBox(message("sdk.create.custom.make.available"))
          .bindSelected(makeAvailable)
      }
    }

    state.scope.launch(start = CoroutineStart.UNDISPATCHED) {
      presenter.projectWithContextFlow.collectLatest { (projectPath, projectLocationContext) ->
        withContext(presenter.uiContext) {
          if (!locationModified) {
            val suggestedVirtualEnvPath = runCatching {
              suggestVirtualEnvPath(projectPath, projectLocationContext)
            }.getOrLogException(LOG)
            location.set(suggestedVirtualEnvPath.orEmpty())
          }
        }
      }
    }
  }

  override fun onShown() {
    val modalityState = ModalityState.current().asContextElement()
    state.scope.launch(Dispatchers.EDT + modalityState) {
      val basePath = suggestVirtualEnvPath(state.projectPath.get(), presenter.projectLocationContext)
      location.set(basePath)
    }
  }

  private suspend fun suggestVirtualEnvPath(projectPath: String, projectLocationContext: ProjectLocationContext): String =
    projectLocationContext.suggestVirtualEnvPath(projectPath)

  override fun getOrCreateSdk(): Sdk {
    val venvRootOnTarget = presenter.getPathOnTarget(Path.of(location.get()))
    return createVirtualEnvSynchronously(state.basePythonVersion.get(),
                                         state.basePythonSdks.get(),
                                         venvRootOnTarget,
                                         state.projectPath.get(),
                                         null, null,
                                         inheritSitePackages = inheritSitePackages.get(),
                                         makeShared = makeAvailable.get())!!
  }

  companion object {
    private val LOG = logger<PythonNewVirtualenvCreator>()

    /**
     * We assume this is the default name of the directory that is located in user home and which contains user virtualenv Python
     * environments.
     *
     * @see com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor.getDefaultLocation
     */
    private const val DEFAULT_VIRTUALENVS_DIR = ".virtualenvs"

    private suspend fun ProjectLocationContext.suggestVirtualEnvPath(projectBasePath: String?): String =
      if (this is LocalContext)
        withContext(Dispatchers.IO) {
          FileUtil.toSystemDependentName(PySdkSettings.instance.getPreferredVirtualEnvBasePath(projectBasePath))
        }
      else suggestVirtualEnvPathGeneral(projectBasePath)


    /**
     * The simplest case of [PySdkSettings.getPreferredVirtualEnvBasePath] implemented.
     */
    private suspend fun ProjectLocationContext.suggestVirtualEnvPathGeneral(projectBasePath: String?): String {
      val suggestedVirtualEnvName = projectBasePath?.let { PathUtil.getFileName(it) } ?: "venv"
      val userHome = fetchUserHomeDirectory()
      return userHome?.resolve(DEFAULT_VIRTUALENVS_DIR)?.resolve(suggestedVirtualEnvName)?.toString().orEmpty()
    }
  }
}