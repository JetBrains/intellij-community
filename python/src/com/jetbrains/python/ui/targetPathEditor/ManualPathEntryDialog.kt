// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.ui.targetPathEditor

import com.intellij.execution.Platform
import com.intellij.execution.target.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.ui.layout.*
import com.jetbrains.python.PyBundle
import java.util.function.Supplier
import javax.swing.JComponent

/**
 * The dialog that allows to specify the path to a file or directory manually.
 *
 * Performs validation that the path that is being added is an absolute path on
 * the specified [platform].
 */
class ManualPathEntryDialog(private val project: Project?,
                            private val platform: Platform = Platform.UNIX,
                            targetConfig: TargetEnvironmentConfiguration? = null) : DialogWrapper(project) {

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
        val textFieldComponent = if (targetConfigAndType == null)
          textField(prop = ::path)
        else
          textFieldWithBrowseTargetButton(this, targetConfigAndType.second, Supplier { targetConfigAndType.first }, project!!, label, this@ManualPathEntryDialog::path.toBinding(), TargetBrowserHints(true))
        textFieldComponent.withValidationOnApply { textField ->
          val text = textField.text
          when {
            text.isBlank() -> error(PyBundle.message("path.must.not.be.empty.error.message"))
            !isAbsolutePath(text, platform) -> error(PyBundle.message("path.must.be.absolute.error.message"))
            text.endsWith(" ") -> warning(PyBundle.message("path.ends.with.whitespace.warning.message"))
            else -> null
          }
        }.focused()
      }
    }
  }

  companion object {
    fun isAbsolutePath(path: String, platform: Platform): Boolean = when (platform) {
      Platform.UNIX -> path.startsWith("/")
      Platform.WINDOWS -> isAbsoluteWindowsPath(path)
    }

    private fun isAbsoluteWindowsPath(path: String): Boolean = OSAgnosticPathUtil.isAbsoluteDosPath(path)
  }
}