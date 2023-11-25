// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.promotion

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService
import javax.swing.JComponent

internal abstract class PycharmNewFilePromoAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val builder = DialogBuilder()
    builder.setCenterPanel(createCentralComponent())
    builder.setOkOperation {
      FUSEventSource.ACTIONS.openDownloadPageAndLog(null, PluginAdvertiserService.pyCharmProfessional.downloadUrl, null)
    }
    builder.show()
  }

  abstract fun createCentralComponent(): JComponent
}

internal class NewJupyterNotebookFile : PycharmNewFilePromoAction() {
  override fun createCentralComponent(): JComponent {
    return jupyterFeatures()
  }
}

internal class NewJavaScriptFile : PycharmNewFilePromoAction() {
  override fun createCentralComponent(): JComponent {
    return javascriptFeatures()
  }
}

internal class NewTypeScriptFile : PycharmNewFilePromoAction() {
  override fun createCentralComponent(): JComponent {
    return javascriptFeatures()
  }
}

internal class NewSqlFile : PycharmNewFilePromoAction() {
  override fun createCentralComponent(): JComponent {
    return databaseFeatures()
  }
}