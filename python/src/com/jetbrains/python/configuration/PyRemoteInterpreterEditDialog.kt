// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.execution.target.getTargetType
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.python.sdk.backend.TargetPythonInterpreter
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.python.PyBundle
import com.jetbrains.python.target.PythonLanguageRuntimeType
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.SwingUtilities

/** Minimum preferred width for the "Interpreter Settings" edit dialog — matches the Figma spec. */
private const val EDIT_DIALOG_MIN_WIDTH: Int = 720

/**
 * "Interpreter Settings" edit dialog for a **remote** target interpreter — Docker, Docker
 * Compose, SSH, WSL, and similar. Renders the same right-side form the classical Project
 * Structure > Modules > Interpreter dialog shows. The interpreter keeps its name, so only the
 * target configuration is editable.
 */
internal class PyRemoteInterpreterEditDialog private constructor(
  project: Project,
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
    // The name is not editable: an interpreter keeps its name, see [PyInterpreterEditDialogLauncher].
    dialogPanel = panel {
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
      detailsConfigurable.apply()
    }
    catch (e: ConfigurationException) {
      Messages.showErrorDialog(contentPane, e.messageHtml.toString(), title)
      return
    }
    super.doOKAction()
  }

  override fun dispose() {
    detailsConfigurable.disposeUIResources()
    super.dispose()
  }

  companion object {

    /** Builds the dialog for [interpreter] with the form of its target type. */
    fun createFor(project: Project, interpreter: TargetPythonInterpreter, parent: Configurable): PyRemoteInterpreterEditDialog {
      val targetEnvironmentConfiguration = interpreter.targetConfiguration
      val targetType: TargetEnvironmentType<TargetEnvironmentConfiguration> = targetEnvironmentConfiguration.getTargetType()
      val targetConfigurable = targetType.createConfigurable(
        project,
        targetEnvironmentConfiguration,
        PythonLanguageRuntimeType.Helper.getInstance(),
        parent,
      )
      val detailsConfigurable = PythonTargetInterpreterDetailsConfigurable(interpreter, targetConfigurable)
      return PyRemoteInterpreterEditDialog(project, detailsConfigurable)
    }
  }
}
