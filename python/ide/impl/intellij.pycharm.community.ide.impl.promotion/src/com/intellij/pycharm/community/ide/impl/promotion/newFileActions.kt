// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.promotion

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.ui.PopupBorder
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JRootPane

internal abstract class PycharmNewFilePromoAction(private val promoTopic: PromoTopic) : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val builder = DialogBuilder()
    builder.dialogWrapper.setUndecorated(true)
    val rootPane = (builder.window as JDialog).rootPane
    rootPane.windowDecorationStyle = JRootPane.NONE
    rootPane.border = PopupBorder.Factory.create(true, true)
    builder.setCenterPanel(createCentralComponent())
    val isOk = builder.showAndGet()
    if (isOk) {
      createOpenDownloadPageLambda(PromoEventSource.NEW_FILE, promoTopic).invoke()
    }
  }

  abstract fun createCentralComponent(): JComponent
}

internal class NewJupyterNotebookFile : PycharmNewFilePromoAction(PromoTopic.Jupyter) {
  override fun createCentralComponent(): JComponent {
    return jupyterFeatures(PromoEventSource.NEW_FILE)
  }
}

internal class NewJavaScriptFile : PycharmNewFilePromoAction(PromoTopic.JavaScript) {
  override fun createCentralComponent(): JComponent {
    return javascriptFeatures(PromoEventSource.NEW_FILE, PromoTopic.JavaScript)
  }
}

internal class NewTypeScriptFile : PycharmNewFilePromoAction(PromoTopic.TypeScript) {
  override fun createCentralComponent(): JComponent {
    return javascriptFeatures(PromoEventSource.NEW_FILE, PromoTopic.TypeScript)
  }
}

internal class NewSqlFile : PycharmNewFilePromoAction(PromoTopic.Database) {
  override fun createCentralComponent(): JComponent {
    return databaseFeatures(PromoEventSource.NEW_FILE)
  }
}