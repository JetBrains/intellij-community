// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.EditorTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.debugger.PyDebuggerOptionsProvider
import com.jetbrains.python.psi.impl.PyExpressionCodeFragmentImpl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import java.awt.Dimension
import javax.swing.JComponent

/**
 * The page owns the Debug Console only. Every value it edits is separate from the Python Console one, so a
 * change here never alters the Python Console page.
 */
@ApiStatus.Internal
class PyDebugConsoleConfigurable(private val myProject: Project) : SearchableConfigurable {

  private var myPanel: DialogPanel? = null
  private var myStartScriptEditor: EditorTextField? = null

  override fun getId(): String = ID

  override fun getDisplayName(): @Nls String = PyBundle.message("configurable.PyDebugConsoleConfigurable.display.name")

  override fun createComponent(): JComponent {
    if (myPanel == null) myPanel = createPanel()
    return myPanel!!
  }

  private fun createPanel(): DialogPanel {
    val consoleOptions = PyConsoleOptions.getInstance(myProject)
    val debuggerOptions = PyDebuggerOptionsProvider.getInstance(myProject)
    val editor = createStartScriptEditor(debuggerOptions.debugConsoleStartScript)
    myStartScriptEditor = editor

    return panel {
      row {
        @Suppress("DialogTitleCapitalization") // 'Debug Console' is a name and properly capitalized.
        checkBox(PyBundle.message("form.console.options.always.show.debug.console"))
          .bindSelected(consoleOptions::isShowDebugConsoleByDefault)
      }
      row {
        checkBox(PyBundle.message("form.console.options.use.ipython.if.available"))
          .bindSelected(
            { debuggerOptions.isDebugConsoleIpythonEnabled },
            { debuggerOptions.isDebugConsoleIpythonEnabled = it },
          )
      }
      row {
        checkBox(PyBundle.message("form.console.options.use.command.queue"))
          .bindSelected(
            { debuggerOptions.isDebugConsoleCommandQueueEnabled },
            { debuggerOptions.isDebugConsoleCommandQueueEnabled = it },
          )
      }
      group(PyBundle.message("form.console.specific.options.starting.script"), indent = false) {
        row {
          cell(editor)
            .align(Align.FILL)
            .applyToComponent {
              minimumSize = Dimension(80, 80)
              preferredSize = Dimension(100, 130)
            }
            .onApply { debuggerOptions.debugConsoleStartScript = editor.text }
            .onReset { editor.text = debuggerOptions.debugConsoleStartScript }
            .onIsModified { editor.text != debuggerOptions.debugConsoleStartScript }
        }.resizableRow()
      }
    }
  }

  @TestOnly
  internal fun setStartScriptTextForTest(text: String) {
    requireNotNull(myStartScriptEditor) { "createComponent() was not called" }.text = text
  }

  private fun createStartScriptEditor(text: String): EditorTextField {
    return object : EditorTextField(createDocument(text), myProject, PythonFileType.INSTANCE) {
      override fun createEditor(): EditorEx = super.createEditor().also { it.setVerticalScrollbarVisible(true) }

      override fun isOneLineMode(): Boolean = false
    }
  }

  private fun createDocument(text: String): Document = ReadAction.compute<Document, RuntimeException> {
    val fragment = PyExpressionCodeFragmentImpl(myProject, "debug_console_start_script.py", text.trim(), true)
    requireNotNull(PsiDocumentManager.getInstance(myProject).getDocument(fragment))
  }

  override fun isModified(): Boolean = myPanel?.isModified() == true

  override fun apply() {
    myPanel?.apply()
  }

  override fun reset() {
    myPanel?.reset()
  }

  override fun disposeUIResources() {
    myPanel = null
    myStartScriptEditor = null
  }

  companion object {
    const val ID: String = "python.debug.console"
  }
}
