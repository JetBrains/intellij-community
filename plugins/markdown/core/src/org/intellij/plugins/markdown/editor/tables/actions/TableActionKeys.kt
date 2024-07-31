// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.actions

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import java.awt.Container
import java.lang.ref.WeakReference
import javax.swing.JComponent

internal object TableActionKeys {
  val ELEMENT = DataKey.create<WeakReference<PsiElement>>("TableBarElement")
  val COLUMN_INDEX = DataKey.create<Int>("TableBarColumnIndex")

  private class TableWrappingBackgroundDataProvider(
    private val base: JComponent,
    private val provider: UiDataProvider
  ) : JComponent(), UiDataProvider {
    override fun getParent(): Container = base

    override fun isShowing() = true

    override fun uiDataSnapshot(sink: DataSink) {
      DataSink.uiDataSnapshot(sink, provider)
    }
  }

  fun createDataContextComponent(editor: Editor, dataProvider: UiDataProvider): JComponent {
    return TableWrappingBackgroundDataProvider(editor.contentComponent, dataProvider)
  }
}
