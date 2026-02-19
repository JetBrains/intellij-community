// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel

internal class PySetPropertyAction : DumbAwareAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val key = AtomicProperty("")
    val value = AtomicProperty("")
    val panel = panel {
      row("Key:") {
        textField().bindText(key).align(Align.FILL)
      }
      row("Value:") {
        textField().bindText(value).align(Align.FILL)
      }
    }

    val result = dialog("Set value in PropertiesComponent", panel, false).showAndGet()
    if (result) {
      PropertiesComponent.getInstance().setValue(key.get(), value.get())
    }
  }
}