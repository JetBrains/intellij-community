// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.ui.targetPathEditor

import com.intellij.execution.target.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toMutableProperty
import com.jetbrains.python.PyBundle
import com.jetbrains.python.pathValidation.PlatformAndRoot.Companion.getPlatformAndRoot
import com.jetbrains.python.pathValidation.ValidationRequest
import com.jetbrains.python.pathValidation.validateExecutableFile
import com.jetbrains.python.ui.pyModalBlocking
import java.util.function.Supplier
import javax.swing.JComponent

/**
 * The dialog that allows to specify the path to a file or directory manually.
 * Performs validation that the path that is being added is an absolute path on
 *
 * It must be used for remote FS only. No local FS supported.
 */
class ManualPathEntryDialog(private val project: Project?,
                            targetConfig: TargetEnvironmentConfiguration? = null)
  : DialogWrapper(project) {

  private val targetConfigAndType: Pair<TargetEnvironmentConfiguration, BrowsableTargetEnvironmentType>? =
    (targetConfig?.getTargetType() as? BrowsableTargetEnvironmentType)?.let { Pair(targetConfig, it) }
  var path: String = ""
    private set

  init {
    title = PyBundle.message("enter.path.dialog.title")
    init()
  }

  override fun createCenterPanel(): JComponent {
    val label = PyBundle.message("path.label")
    return panel {
      row(label = label) {
        val textFieldComponent = if (targetConfigAndType == null || project == null)
          textField().bindText(::path)
        else
          textFieldWithBrowseTargetButton(targetConfigAndType.second, Supplier { targetConfigAndType.first }, project, label,
                                          this@ManualPathEntryDialog::path.toMutableProperty(), TargetBrowserHints(true))
        textFieldComponent.validationOnApply { textField ->
          val text = textField.text
          return@validationOnApply pyModalBlocking {
            // this dialog is always for remote
            validateExecutableFile(
              ValidationRequest(text, platformAndRoot = targetConfigAndType?.first.getPlatformAndRoot(defaultIsLocal = false)))
          }
        }.focused()
      }
    }
  }
}