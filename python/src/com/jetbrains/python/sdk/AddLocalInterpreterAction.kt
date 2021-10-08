// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PyBundle
import com.jetbrains.python.configuration.PyConfigurableInterpreterList
import com.jetbrains.python.sdk.add.target.PyAddTargetBasedSdkDialog
import java.util.function.Consumer

internal class AddLocalInterpreterAction(private val project: Project,
                                         private val module: Module,
                                         private val onSdkCreated: (sdk: Sdk) -> Unit)
  : AnAction(PyBundle.messagePointer("python.sdk.action.add.local.interpreter.text"), AllIcons.Nodes.HomeFolder) {
  override fun actionPerformed(e: AnActionEvent) {
    val model = PyConfigurableInterpreterList.getInstance(project).model

    PyAddTargetBasedSdkDialog.show(
      project,
      module,
      model.sdks.asList(),
      Consumer {
        if (it != null && model.findSdk(it.name) == null) {
          model.addSdk(it)
          model.apply()
          onSdkCreated(it)
        }
      }
    )
  }
}