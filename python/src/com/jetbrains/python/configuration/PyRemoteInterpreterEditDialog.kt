// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.execution.target.getTargetType
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.ValidationInfoBuilder
import com.jetbrains.python.PyBundle
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.target.PythonLanguageRuntimeType
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * "Interpreter Settings" edit dialog for a **remote** target-aware Python SDK — Docker, Docker
 * Compose, SSH, WSL, and similar. Renders the same right-side form the classical Project
 * Structure > Modules > Interpreter dialog shows, with an added **Interpreter Name** row so the
 * rename flow lives inside the same dialog rather than behind a separate action.
 *
 * State and persistence live in [PyRemoteInterpreterEditModel]; the dialog binds the name field
 * via Kotlin UI DSL v2 and hands OK to the model's `apply`.
 */
internal class PyRemoteInterpreterEditDialog private constructor(
  project: Project,
  private val model: PyRemoteInterpreterEditModel,
  private val detailsConfigurable: PythonTargetInterpreterDetailsConfigurable,
) : DialogWrapper(project, true) {

  private lateinit var dialogPanel: DialogPanel

  init {
    title = PyBundle.message("configurable.PyAllInterpretersConfigurable.edit.dialog.title")
    setOKButtonText(PyBundle.message("configurable.PyAllInterpretersConfigurable.edit.dialog.save"))
    init()
  }

  override fun createCenterPanel(): JComponent {
    // `details` is the Swing form the target-specific `Configurable` (Docker / SSH / WSL / …)
    // renders — the same form the classical Project Structure > Modules > Interpreter dialog
    // hosts. `Configurable.createComponent()` is nullable per the platform contract:
    // implementations return `null` when the UI cannot be built (target runtime disposed between
    // `createFor` and dialog open, or the target type opts out of a UI form). Render an empty
    // panel in that edge case instead of crashing the OK path.
    val details = detailsConfigurable.createComponent() ?: return panel { }
    dialogPanel = panel {
      row(PyBundle.message("configurable.PyAllInterpretersConfigurable.edit.dialog.interpreter.name")) {
        // Same validator on input + on apply — flags a duplicate name as the user types and again on OK.
        val nameValidator: ValidationInfoBuilder.(JBTextField) -> ValidationInfo? = { field ->
          model.validateName(field.text)?.let { error(it) }
        }
        textField()
          .bindText(model::name)
          .align(AlignX.FILL)
          .validationOnInput(nameValidator)
          .validationOnApply(nameValidator)
      }
      row {
        cell(details).align(AlignX.FILL)
      }
    }
    dialogPanel.preferredSize = Dimension(EDIT_DIALOG_MIN_WIDTH, dialogPanel.preferredSize.height)
    // Target-runtime forms hydrate their fields asynchronously (Docker image list, SSH tunnels, …),
    // so the dialog's initial `pack()` may size the window against a still-collapsing details
    // panel. When Swing signals the form finished attaching (`SHOWING_CHANGED`), re-pack the
    // enclosing dialog window (`SwingUtilities.getWindowAncestor` walks up from `details` to the
    // `JDialog` DialogWrapper owns) and enforce the Figma-spec minimum width. `null` here means
    // Swing tore the component off the hierarchy before the invokeLater fired — safe to skip.
    details.addHierarchyListener { event ->
      if ((event.changeFlags and java.awt.event.HierarchyEvent.SHOWING_CHANGED.toLong()) == 0L) return@addHierarchyListener
      SwingUtilities.invokeLater {
        val window = SwingUtilities.getWindowAncestor(details) ?: return@invokeLater
        window.pack()
        if (window.width < EDIT_DIALOG_MIN_WIDTH) window.setSize(EDIT_DIALOG_MIN_WIDTH, window.height)
      }
    }
    return dialogPanel
  }

  override fun doOKAction() {
    dialogPanel.apply()
    try {
      model.apply()
    }
    catch (e: ConfigurationException) {
      Messages.showErrorDialog(contentPane, e.messageHtml.toString(), title)
      return
    }
    detailsConfigurable.apply()
    super.doOKAction()
  }

  override fun dispose() {
    detailsConfigurable.disposeUIResources()
    super.dispose()
  }

  companion object {

    /**
     * Builds the dialog for [sdk] with its target-specific form, or `null` when [sdk] is a legacy
     * remote SDK whose target information has been lost. The caller (launcher) treats a `null`
     * return as "not editable", same as it does for a non-target-aware broken SDK.
     */
    fun createFor(project: Project, sdk: Sdk, parent: Configurable): PyRemoteInterpreterEditDialog? {
      val additionalData = sdk.sdkAdditionalData as? PyTargetAwareAdditionalData ?: return null
      val targetEnvironmentConfiguration = additionalData.targetEnvironmentConfiguration ?: return null
      val targetType: TargetEnvironmentType<TargetEnvironmentConfiguration> = targetEnvironmentConfiguration.getTargetType()
      val targetConfigurable = targetType.createConfigurable(
        project,
        targetEnvironmentConfiguration,
        PythonLanguageRuntimeType.Helper.getInstance(),
        parent,
      )
      val detailsConfigurable = PythonTargetInterpreterDetailsConfigurable(project, sdk, additionalData, targetConfigurable)
      val model = PyRemoteInterpreterEditModel(project, sdk)
      return PyRemoteInterpreterEditDialog(project, model, detailsConfigurable)
    }
  }
}
