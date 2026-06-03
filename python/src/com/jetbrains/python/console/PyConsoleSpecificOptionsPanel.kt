// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.EditorTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.psi.impl.PyExpressionCodeFragmentImpl
import com.jetbrains.python.run.AbstractPyCommonOptionsForm
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.run.PyCommonOptionsFormData
import com.jetbrains.python.run.PyCommonOptionsFormFactory
import java.awt.Dimension
import javax.swing.JComponent

internal class PyConsoleSpecificOptionsPanel(private val project: Project) {
  private lateinit var consoleSettings: PyConsoleOptions.PyConsoleSettings
  private lateinit var editorTextField: EditorTextField
  private lateinit var commonOptionsForm: AbstractPyCommonOptionsForm

  fun createPanel(optionsProvider: PyConsoleOptions.PyConsoleSettings): JComponent {
    consoleSettings = optionsProvider
    commonOptionsForm = PyCommonOptionsFormFactory.getInstance().createForm(createCommonOptionsFormData(project)).also {
      it.subscribe()
    }
    editorTextField = createEditorTextField(optionsProvider.myCustomStartScript)

    return createPanel()
  }

  fun apply() {
    consoleSettings.myCustomStartScript = editorTextField.text
    consoleSettings.apply(commonOptionsForm)
  }

  val isModified: Boolean
    get() = editorTextField.text != consoleSettings.myCustomStartScript || consoleSettings.isModified(commonOptionsForm)

  fun reset() {
    UIUtil.invokeLaterIfNeeded { editorTextField.text = consoleSettings.myCustomStartScript }
    consoleSettings.reset(project, commonOptionsForm)
  }

  private fun createPanel(): DialogPanel {
    return panel {
      row {
        cell(commonOptionsForm.mainPanel).align(AlignX.FILL)
      }
      group(PyBundle.message("form.console.specific.options.starting.script"), indent = false) {
        row {
          cell(editorTextField)
            .align(Align.FILL)
            .applyToComponent {
              minimumSize = Dimension(80, 80)
              preferredSize = Dimension(100, 130)
            }
        }.resizableRow()
      }
    }
  }

  private fun createCommonOptionsFormData(project: Project): PyCommonOptionsFormData {
    return object : PyCommonOptionsFormData {
      override fun getProject(): Project = project
      override fun getValidModules(): List<Module> = AbstractPythonRunConfiguration.getValidModules(project)
      override fun showConfigureInterpretersLink(): Boolean = true
    }
  }

  private fun createEditorTextField(text: String): EditorTextField {
    return object : EditorTextField(createDocument(project, text), project, PythonFileType.INSTANCE) {
      override fun createEditor(): EditorEx {
        return super.createEditor().also {
          it.setVerticalScrollbarVisible(true)
        }
      }

      override fun isOneLineMode(): Boolean = false
    }
  }

  private fun createDocument(project: Project, text: String): Document {
    val fragment = PyExpressionCodeFragmentImpl(project, "start_script.py", text.trim(), true)
    return requireNotNull(PsiDocumentManager.getInstance(project).getDocument(fragment))
  }
}