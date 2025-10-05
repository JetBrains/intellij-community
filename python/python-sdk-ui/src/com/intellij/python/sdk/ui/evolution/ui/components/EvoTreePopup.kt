@file:Suppress("UnstableApiUsage")

package com.intellij.python.sdk.ui.evolution.ui.components

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.util.NlsContexts.PopupTitle
import com.intellij.ui.popup.WizardPopup
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.popup.list.PopupListElementRenderer
import kotlinx.coroutines.CoroutineScope
import java.awt.Component
import javax.swing.JList
import javax.swing.ListCellRenderer

class EvoPopupListElementRenderer(listPopupImpl: ListPopupImpl) : com.intellij.ui.popup.list.PopupListElementRenderer<EvoTreeItem>(listPopupImpl) {
  override fun customizeComponent(list: JList<out EvoTreeItem>, value: EvoTreeItem, isSelected: Boolean) {
    super.customizeComponent(list, value, isSelected)
    myTextLabel.isEnabled = value.isEnabled
  }
}

open class EvoTreePopup private constructor(
  aParent: WizardPopup?,
  step: EvoActionPopupStep,
  val myDisposeCallback: Runnable?,
  dataContext: DataContext,
  maxRowCount: Int
) : com.intellij.ui.popup.list.ListPopupImpl(CommonDataKeys.PROJECT.getData(dataContext), aParent, step, null) {
  private val myComponent: Component? = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext)

  override fun getListElementRenderer(): ListCellRenderer<*>? {
    return EvoPopupListElementRenderer(this)
  }

  init {
    setMaxRowCount(maxRowCount)
    isShowSubmenuOnHover = true
    //registerAction("handleActionToggle1", KeyEvent.VK_SPACE, 0, object : AbstractAction() {
    //  override fun actionPerformed(e: ActionEvent) {
    //    //handleToggleAction(createKeyEvent(e, KeyEvent.VK_SPACE))
    //  }
    //})

    //addListSelectionListener(ListSelectionListener { e: ListSelectionEvent? ->
    //  val list = e!!.getSource() as JList<*>
    //  val evoTreeElement = list.getSelectedValue() as? EvoTreeElement ?: return@ListSelectionListener
    //  ActionMenu.showDescriptionInStatusBar(true, myComponent, evoTreeElement.description)
    //})
  }


  constructor(
    parentPopup: WizardPopup?,
    title: @PopupTitle String?,
    evoTreeNodeElement: EvoTreeNodeElement,
    dataContext: DataContext,
    scope: CoroutineScope,
    maxRowCount: Int,
    disposeCallback: Runnable?,
  ) : this(
    aParent = parentPopup,
    step = EvoActionPopupStep(
      myTitle = title,
      node = evoTreeNodeElement,
      dataContext = dataContext,
      scope = scope,
    ),
    myDisposeCallback = disposeCallback,
    dataContext = dataContext,
    maxRowCount = maxRowCount
  )


  override fun dispose() {
    myDisposeCallback?.run()
    ActionMenu.showDescriptionInStatusBar(true, myComponent, null)
    super.dispose()
  }

}


