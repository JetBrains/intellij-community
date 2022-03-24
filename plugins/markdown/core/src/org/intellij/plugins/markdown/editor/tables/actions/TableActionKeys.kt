// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.actions

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import java.lang.ref.WeakReference
import javax.swing.JComponent

internal object TableActionKeys {
  val ELEMENT = DataKey.create<WeakReference<PsiElement>>("TableBarElement")
  val COLUMN_INDEX = DataKey.create<Int>("TableBarColumnIndex")

  fun createDataContextWrapperComponent(base: JComponent, provider: DataProvider): JComponent {
    val baseContext = DataManager.getInstance().getDataContext(base)
    return object: JComponent(), DataProvider {
      override fun isShowing() = true

      override fun getData(dataId: String): Any? {
        return provider.getData(dataId) ?: baseContext.getData(dataId)
      }
    }
  }

  fun createActionToolbar(group: ActionGroup, isHorizontal: Boolean, editor: Editor, dataProvider: DataProvider? = null): ActionToolbar {
    val actionToolbar = object: ActionToolbarImpl(ActionPlaces.EDITOR_POPUP, group, isHorizontal) {
      override fun addNotify() {
        super.addNotify()
        updateActionsImmediately(true)
      }
    }
    val dataContextComponent = dataProvider?.let { createDataContextWrapperComponent(editor.contentComponent, dataProvider) }
    actionToolbar.targetComponent = dataContextComponent
    actionToolbar.adjustTheSameSize(true)
    actionToolbar.setReservePlaceAutoPopupIcon(false)
    return actionToolbar
  }
}
