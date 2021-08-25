// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ui

import com.intellij.execution.Platform
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.layout.*
import com.jetbrains.python.PyBundle
import javax.swing.JComponent

/**
 * The dialog that allows to specify the path to a file or directory manually.
 *
 * Performs validation that the path that is being added is an absolute path on
 * the specified [platform].
 */
class ManualPathEntryDialog(project: Project?, private val platform: Platform) : DialogWrapper(project) {
  var path: String = ""
    private set

  init {
    title = PyBundle.message("enter.path.dialog.title")
    init()
  }

  override fun createCenterPanel(): JComponent =
    panel {
      row(label = PyBundle.message("path.label")) {
        textField(prop = ::path).withValidationOnApply { textField ->
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

  companion object {
    fun isAbsolutePath(path: String, platform: Platform): Boolean = when (platform) {
      Platform.UNIX -> path.startsWith("/")
      Platform.WINDOWS -> isAbsoluteWindowsPath(path)
    }

    private fun isAbsoluteWindowsPath(path: String): Boolean =
      path.length > 2 && path[1] == ':' && isDriveLetter(path[0]) && path[2] in setOf('\\', '/')

    private fun isDriveLetter(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z'
  }
}