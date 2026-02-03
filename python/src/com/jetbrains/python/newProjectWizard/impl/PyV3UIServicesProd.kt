// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard.impl

import com.intellij.ide.projectView.impl.AbstractProjectViewPane
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.ui.tree.ui.DefaultTreeUI.AUTO_EXPAND_ALLOWED
import com.intellij.util.ui.showingScope
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.newProjectWizard.PyV3UIServices
import com.jetbrains.python.util.ShowingMessageErrorSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.swing.JComponent

internal object PyV3UIServicesProd : PyV3UIServices {
  override fun runWhenComponentDisplayed(component: JComponent, code: suspend CoroutineScope.() -> Unit) {
    component.showingScope("..") {
      code()
    }
  }

  override val errorSink: ErrorSink = ShowingMessageErrorSync

  override suspend fun expandProjectTreeView(project: Project): Unit = withContext(Dispatchers.EDT) {
    // Null means no project pane opened
    val tree = AbstractProjectViewPane.EP.getExtensions(project).firstNotNullOfOrNull { pane -> pane.tree } ?: return@withContext
    with(tree) {
      // Project view expands lonely branch if it is the only child of its parent
      // As `.venv` is usually the only child of project directory it is opened automatically.
      // No need to do that
      putClientProperty(AUTO_EXPAND_ALLOWED, false)
      expandRow(0)
      putClientProperty(AUTO_EXPAND_ALLOWED, true)
    }
  }
}
