// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.execution.EnvFilesOptions
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configuration.EnvFilesDialog
import com.intellij.execution.configuration.addEnvFile
import com.intellij.execution.ui.SettingsEditorFragment
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.UserActivityProviderComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.VerticalComponentGap
import com.intellij.util.containers.ContainerUtil
import java.awt.BorderLayout
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import javax.swing.event.DocumentEvent

class EnvFileComponent(workingDir: () -> VirtualFile?) : LabeledComponent<TextFieldWithBrowseButton?>(), UserActivityProviderComponent {
  val envFilesComponent: EnvironmentFilePathTextFieldWithBrowseButton = EnvironmentFilePathTextFieldWithBrowseButton(workingDir)

  init {
    setComponent(envFilesComponent)
    labelLocation = BorderLayout.WEST
    setText(ExecutionBundle.message("environment.variables.filepath.component.title"))
    putClientProperty(DslComponentProperty.INTERACTIVE_COMPONENT, envFilesComponent.childComponent)
    putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap.BOTH)
  }

  override fun addChangeListener(changeListener: ChangeListener) {
    envFilesComponent.addChangeListener(changeListener)
  }

  override fun removeChangeListener(changeListener: ChangeListener) {
    envFilesComponent.removeChangeListener(changeListener)
  }

  companion object {
    fun <T : EnvFilesOptions> createEnvFilesFragment(workingDir: () -> VirtualFile?): SettingsEditorFragment<T, *> {
      val envFile = EnvFileComponent(workingDir)
      val fragment = SettingsEditorFragment<T, JComponent>(
        "envPaths",
        ExecutionBundle.message("environment.variables.filepath.fragment.name"),
        ExecutionBundle.message("group.operating.system"),
        envFile,
        { config, _ ->
          envFile.envFilesComponent.envFilePaths = config.envFilePaths
        },
        { config, _ ->
          if (!envFile.isVisible) {
            config.envFilePaths = emptyList()
          }
          else {
            config.envFilePaths = envFile.envFilesComponent.envFilePaths
          }
        },
        { true })

      fragment.isCanBeHidden = true
      fragment.actionHint = ExecutionBundle.message("set.custom.environment.variables.file.for.the.process")

      return fragment
    }
  }
}

class EnvironmentFilePathTextFieldWithBrowseButton(private val workingDir: () -> VirtualFile?)
  : TextFieldWithBrowseButton.NoPathCompletion(), UserActivityProviderComponent {
  var envFilePaths: List<String>
    set(value) {
      val newText = buildString {
        for (path in value) {
          if (!isEmpty()) {
            append(";")
          }
          append(path)
        }
      }
      if (newText != text) {
        text = newText
      }
    }
    get() {
      val text = getText()
      return text.split(";")
        .dropLastWhile { it.isEmpty() }
        .toTypedArray()
        .map { s -> s.trim { it <= ' ' } }
        .filter { s-> !s.isEmpty() }
    }

  private val listeners: MutableList<ChangeListener> = ContainerUtil.createLockFreeCopyOnWriteList()

  init {
    addActionListener {
      browseForEnvFile()
    }
    textField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        fireStateChanged()
      }
    })
  }

  private fun browseForEnvFile() {
    if (envFilePaths.isEmpty()) {
      addEnvFile(textField, workingDir()) { s: String ->
        envFilePaths = ArrayList(envFilePaths).also { it.add(s) }
      }
    }
    else {
      val dialog = EnvFilesDialog(this, envFilePaths)
      dialog.show()
      if (dialog.isOK) {
        envFilePaths = ArrayList(dialog.paths)
      }
    }
  }

  override fun getTextField(): ExtendableTextField {
    return super.getTextField() as ExtendableTextField
  }

  override fun addChangeListener(changeListener: ChangeListener) {
    listeners.add(changeListener)
  }

  override fun removeChangeListener(changeListener: ChangeListener) {
    listeners.remove(changeListener)
  }

  private fun fireStateChanged() {
    for (listener in listeners) {
      listener.stateChanged(ChangeEvent(this))
    }
  }

  override fun getIconTooltip(): @NlsContexts.Tooltip String {
    return ExecutionBundle.message("specify.environment.file.tooltip") + " (" +
           KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)) + ")"
  }
}