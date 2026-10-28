// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.DialogPanel
import com.intellij.python.sdk.common.PyInterpreterItem
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.text
import com.jetbrains.python.PyBundle

/**
 * Shows a local Python interpreter. An interpreter keeps its binary and its owner, so nothing here is editable.
 */
internal class PythonLocalInterpreterConfigurable(
  private val sdk: Sdk,
  private val item: PyInterpreterItem,
) : BoundConfigurable(sdk.name) {
  override fun createPanel(): DialogPanel = panel {
    row(PyBundle.message("form.edit.sdk.interpreter.path")) {
      textField()
        .text(sdk.homePath.orEmpty())
        .applyToComponent { isEditable = false }
        .align(AlignX.FILL)
        // Why the list flags this interpreter, stated where it needs no hover. Until PY-91967 the reason reached
        // `idea.log` only, so the marker in the list was the whole of what the user was told.
        .apply { item.problem?.reason?.let { comment(it) } }
    }
  }
}
