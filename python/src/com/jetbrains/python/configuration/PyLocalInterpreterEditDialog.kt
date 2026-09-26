// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.PythonSdkType
import java.awt.Dimension
import java.nio.file.Path
import javax.swing.JComponent

/** Minimum preferred width for the "Interpreter Settings" edit dialog — matches the Figma spec. */
internal const val EDIT_DIALOG_MIN_WIDTH: Int = 720

/**
 * Compact "Interpreter Settings" dialog for a **local** Python SDK — the form the redesigned
 * "All Interpreters" page (PY-89840) opens from its per-row Edit action.
 *
 * The dialog is a thin binding layer over [PyLocalInterpreterEditModel]: it renders the two
 * controls, binds them via Kotlin UI DSL v2 property references, and hands OK to the model.
 * All persistence logic — modificator commit, association update, project save, path re-detect —
 * lives in the model and is covered by [PyLocalInterpreterEditModelTest].
 *
 * OK closes the dialog immediately and persists in the project's [PyPackageCoroutine] scope. A
 * modal progress on OK would block the EDT for the entire commit / save / re-detect chain (100 ms
 * for a warm SDK, seconds for a cold one); the async close matches what the redesigned "All
 * Interpreters" page does everywhere else it mutates an SDK.
 */
internal class PyLocalInterpreterEditDialog(
  private val project: Project,
  private val model: PyLocalInterpreterEditModel,
) : DialogWrapper(project, true) {

  private lateinit var dialogPanel: DialogPanel

  init {
    title = PyBundle.message("configurable.PyAllInterpretersConfigurable.edit.dialog.title")
    setOKButtonText(PyBundle.message("configurable.PyAllInterpretersConfigurable.edit.dialog.save"))
    init()
  }

  override fun createCenterPanel(): JComponent {
    dialogPanel = panel {
      row(PyBundle.message("form.edit.sdk.interpreter.path")) {
        textFieldWithBrowseButton(
          PythonSdkType.getInstance().homeChooserDescriptor
            .withTitle(PyBundle.message("sdk.edit.dialog.specify.interpreter.path")),
          project,
        ) { it.name }
          .bindText({ model.homePath.toString() }, { model.homePath = Path.of(it) })
          .align(AlignX.FILL)
      }
      if (model.canAssociate) {
        row {
          checkBox(PyBundle.message("form.edit.sdk.associate.this.virtual.environment.with.current.project"))
            .bindSelected(model::associated)
        }
      }
    }
    // Match the Figma width — the path row is a single AlignX.FILL text field, and a compact
    // default DialogWrapper renders it narrow enough that `~/PycharmProjects/…/.venv` truncates.
    dialogPanel.preferredSize = Dimension(EDIT_DIALOG_MIN_WIDTH, dialogPanel.preferredSize.height)
    return dialogPanel
  }

  override fun doOKAction() {
    dialogPanel.apply()
    PyPackageCoroutine.launch(project) {
      model.apply()
    }
    super.doOKAction()
  }
}
