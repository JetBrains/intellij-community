// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.newProject

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonModuleTypeBase
import com.jetbrains.python.newProject.steps.ProjectSpecificSettingsStep
import com.jetbrains.python.newProject.steps.PyAddExistingSdkPanel
import com.jetbrains.python.sdk.PySdkProvider
import com.jetbrains.python.sdk.PySdkSettings
import com.jetbrains.python.sdk.add.PyAddNewCondaEnvPanel
import com.jetbrains.python.sdk.add.PyAddNewVirtualEnvPanel
import com.jetbrains.python.sdk.add.PyAddSdkPanel
import com.jetbrains.python.sdk.pythonSdk
import java.nio.file.Path
import kotlin.streams.toList

/**
 * A wizard for creating new pure-Python projects in IntelliJ.
 *
 * It suggests creating a new Python virtual environment for your new project to follow Python best practices.
 */
class PythonNewProjectWizard : LanguageNewProjectWizard {
  override val name: String = "Python"
  override val ordinal = 600

  override fun createStep(parent: NewProjectWizardLanguageStep): NewProjectWizardStep = NewPythonProjectStep(parent)
}

/**
 * Data for sharing among the steps of the new Python project wizard.
 */
interface NewProjectWizardPythonData : NewProjectWizardBaseData {
  /**
   * A property for tracking changes in [pythonSdk].
   */
  val pythonSdkProperty: GraphProperty<Sdk?>

  /**
   * The Python SDK for the new Python project or module.
   *
   * During [NewProjectWizardStep.setupUI] it reflects the selected Python SDK (it may be `null` for a new environment or if there is no
   * Python installed on the machine). After [PythonSdkStep] gets or creates the actual SDK for the new project in its
   * [NewProjectWizardStep.setupProject], the attribute contains the actual SDK.
   */
  var pythonSdk: Sdk?

  /**
   * The Python module after it has been created during [NewProjectWizardStep.setupProject].
   */
  val module: Module?

  companion object {
    val KEY = Key.create<NewProjectWizardPythonData>(NewProjectWizardPythonData::class.java.name)

    val NewProjectWizardStep.pythonData get() = data.getUserData(KEY)!!

    val NewProjectWizardStep.pythonSdkProperty get() = pythonData.pythonSdkProperty
    val NewProjectWizardStep.pythonSdk get() = pythonData.pythonSdk
    val NewProjectWizardStep.module get() = pythonData.module
  }
}

/**
 * A new Python project wizard step that allows you to either create a new Python environment or select an existing Python interpreter.
 *
 * It works for both PyCharm (where the *.iml file resides in .idea/ directory and the SDK is set for the project) and other
 * IntelliJ-based IDEs (where the *.iml file resides in the module directory and the SDK is set for the module).
 */
class NewPythonProjectStep<P>(parent: P)
  : AbstractNewProjectWizardStep(parent),
    NewProjectWizardBaseData by parent,
    NewProjectWizardPythonData
  where P : NewProjectWizardStep, P : NewProjectWizardBaseData {

  override val pythonSdkProperty: GraphProperty<Sdk?> = propertyGraph.graphProperty { null }
  override var pythonSdk: Sdk? by pythonSdkProperty
  override val module: Module?
    get() = intellijModule ?: context.project?.let { ModuleManager.getInstance(it).modules.firstOrNull() }

  private var intellijModule: Module? = null
  private val sdkStep: PythonSdkStep<NewPythonProjectStep<P>> by lazy { PythonSdkStep(this) }

  override fun setupUI(builder: Panel) {
    sdkStep.setupUI(builder)
  }

  override fun setupProject(project: Project) {
    commitIntellijModule(project)
    sdkStep.setupProject(project)
    setupSdk(project)
  }

  private fun commitIntellijModule(project: Project) {
    val moduleName = name
    val projectPath = Path.of(path, name)
    val moduleBuilder = PythonModuleTypeBase.getInstance().createModuleBuilder().apply {
      name = moduleName
      contentEntryPath = projectPath.toString()
      moduleFilePath = projectPath.resolve(moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION).toString()
    }
    intellijModule = moduleBuilder.commit(project)?.firstOrNull()
  }

  private fun setupSdk(project: Project) {
    var sdk = pythonSdk ?: return
    val existingSdk = ProjectJdkTable.getInstance().findJdk(sdk.name)
    if (existingSdk != null) {
      pythonSdk = existingSdk
      sdk = existingSdk
    }
    else {
      SdkConfigurationUtil.addSdk(sdk)
    }
    val module = intellijModule
    if (module != null) {
      module.pythonSdk = sdk
    }
    else {
      SdkConfigurationUtil.setDirectoryProjectSdk(project, sdk)
    }
  }

  init {
    data.putUserData(NewProjectWizardPythonData.KEY, this)
  }
}

/**
 * A new Python project wizard step that allows you to get or create a Python SDK for your [path]/[name].
 *
 * The resulting SDK is available as [pythonSdk]. The SDK may have not been saved to the project JDK table yet.
 */
class PythonSdkStep<P>(parent: P)
  : AbstractNewProjectWizardMultiStepBase(parent),
    NewProjectWizardPythonData by parent
  where P : NewProjectWizardStep, P : NewProjectWizardPythonData {

  override val label: String = PyBundle.message("python.sdk.new.project.environment")

  override fun initSteps(): Map<String, NewProjectWizardStep> {
    val existingSdkPanel = PyAddExistingSdkPanel(null, null, existingSdks(context), "$path/$name", null)
    return mapOf(
      "New" to NewEnvironmentStep(this),
      // TODO: Handle remote project creation for remote SDKs
      "Existing" to PythonSdkPanelAdapterStep(this, existingSdkPanel),
    )
  }

  override fun setupUI(builder: Panel) {
    super.setupUI(builder)
    step = if (PySdkSettings.instance.useNewEnvironmentForNewProject) "New" else "Existing"
  }

  override fun setupProject(project: Project) {
    super.setupProject(project)
    PySdkSettings.instance.useNewEnvironmentForNewProject = step == "New"
  }
}

/**
 * A new Python project wizard step that allows you to create a new Python environment of various types.
 *
 * The following environment types are supported:
 *
 * * Virtualenv
 * * Conda
 * * Pipenv
 *
 * as well as other environments offered by third-party plugins via [PySdkProvider.createNewEnvironmentPanel].
 *
 * The suggested new environment path for some types of Python environments depends on the path of your new project.
 */
private class NewEnvironmentStep<P>(parent: P)
  : AbstractNewProjectWizardMultiStepBase(parent),
    NewProjectWizardPythonData by parent
  where P : NewProjectWizardStep, P : NewProjectWizardPythonData {

  override val label: String = PyBundle.message("python.sdk.new.project.environment.type")

  override fun initSteps(): Map<String, PythonSdkPanelAdapterStep<NewEnvironmentStep<P>>> {
    val sdks = existingSdks(context)
    val newProjectPath = "$path/$name"
    val basePanels = listOf(
      PyAddNewVirtualEnvPanel(null, null, sdks, newProjectPath, context),
      PyAddNewCondaEnvPanel(null, null, sdks, newProjectPath),
    )
    val providedPanels = PySdkProvider.EP_NAME.extensions()
      .map { it.createNewEnvironmentPanel(null, null, sdks, newProjectPath, context) }
      .toList()
    val panels = basePanels + providedPanels
    return panels
      .associateBy { it.envName }
      .mapValues { (_, v) -> PythonSdkPanelAdapterStep(this, v) }
  }

  override fun setupUI(builder: Panel) {
    super.setupUI(builder)
    val preferred = PySdkSettings.instance.preferredEnvironmentType
    step = if (preferred != null && preferred in steps.keys) preferred else steps.keys.first()
  }

  override fun setupProject(project: Project) {
    super.setupProject(project)
    PySdkSettings.instance.preferredEnvironmentType = step
  }
}

/**
 * A new Python project wizard step that allows you to get or create a Python environment via its [PyAddSdkPanel].
 */
private class PythonSdkPanelAdapterStep<P>(parent: P, val panel: PyAddSdkPanel)
  : AbstractNewProjectWizardStep(parent),
    NewProjectWizardPythonData by parent
  where P : NewProjectWizardStep, P : NewProjectWizardPythonData {

  val panelChangedProperty = propertyGraph.graphProperty {}
  var panelChanged by panelChangedProperty

  override fun setupUI(builder: Panel) {
    with(builder) {
      row {
        cell(panel)
          .graphProperty(panelChangedProperty)
          .horizontalAlign(HorizontalAlign.FILL)
          .validationOnInput { panel.validateAll().firstOrNull() }
          .validationOnApply { panel.validateAll().firstOrNull() }
      }
    }
    panel.addChangeListener {
      panelChanged = Unit
      pythonSdk = panel.sdk
    }
    nameProperty.afterChange { updateNewProjectPath() }
    pathProperty.afterChange { updateNewProjectPath() }
  }

  override fun setupProject(project: Project) {
    pythonSdk = panel.getOrCreateSdk()
  }

  private fun updateNewProjectPath() {
    panel.newProjectPath = "$path/$name"
  }
}

/**
 * Return the list of already configured Python SDKs.
 */
private fun existingSdks(context: WizardContext): List<Sdk> {
  val sdksModel = ProjectSdksModel().apply {
    reset(context.project)
    Disposer.register(context.disposable, Disposable {
      disposeUIResources()
    })
  }
  return ProjectSpecificSettingsStep.getValidPythonSdks(sdksModel.sdks.toList())
}
