package com.intellij.settingsSync.config

import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.settingsSync.SettingsSyncBundle.message
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ThreeStateCheckBox
import com.intellij.util.ui.ThreeStateCheckBox.State
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

object SettingsSyncPanelFactory {
  fun createPanel(syncLabel: @Nls String): DialogPanel {
    return panel {
      row {
        label(syncLabel)
      }
      row {
        SettingsCategoryDescriptor.listAll().forEach { descriptor ->
          row {
            cell {
              if (descriptor.secondaryGroup == null) {
                checkBox(
                  descriptor.name,
                  { descriptor.isSynchronized },
                  { descriptor.isSynchronized = it }
                )
                  .onReset { descriptor.reset() }
                  .onApply { descriptor.apply() }
                  .onIsModified { descriptor.isModified() }
                commentNoWrap(descriptor.description)
              }
              else {
                val topCheckBox = ThreeStateCheckBox(descriptor.name)
                topCheckBox.isThirdStateEnabled = false
                topCheckBox.addActionListener {
                  descriptor.isSynchronized = topCheckBox.state != State.NOT_SELECTED
                  descriptor.secondaryGroup.getDescriptors().forEach {
                    it.isSelected = descriptor.isSynchronized
                  }
                }
                val updateTopCheckBox = {
                  topCheckBox.state = getGroupState(descriptor.secondaryGroup)
                  descriptor.isSynchronized = topCheckBox.state != State.NOT_SELECTED
                }
                component(topCheckBox)
                  .onReset {
                    descriptor.reset()
                    updateTopCheckBox()
                  }
                  .onApply {
                    descriptor.isSynchronized = topCheckBox.state != State.NOT_SELECTED
                    descriptor.apply()
                  }
                  .onIsModified { descriptor.isModified() }
                commentNoWrap(descriptor.description)
                component(configureLink(descriptor.secondaryGroup, updateTopCheckBox))
              }
            }
          }
        }
      }
    }
  }

  private fun getGroupState(group: SettingsSyncSubcategoryGroup): State {
    val descriptors = group.getDescriptors()
    if (descriptors.isEmpty()) return State.NOT_SELECTED
    val isFirstSelected = descriptors.first().isSelected
    descriptors.forEach {
      if (it.isSelected != isFirstSelected) return State.DONT_CARE
    }
    return when {
      isFirstSelected -> State.SELECTED
      group.isComplete() -> State.NOT_SELECTED
      else -> State.DONT_CARE
    }
  }

  private fun configureLink(group: SettingsSyncSubcategoryGroup, onCheckBoxChange: () -> Unit): JComponent {
    val actionLink = ActionLink(message("subcategory.config.link")) {}
    actionLink.addActionListener {
      showSubcategories(actionLink, group.getDescriptors(), onCheckBoxChange)
    }
    actionLink.setDropDownLinkIcon()
    return actionLink
  }

  private fun showSubcategories(owner: JComponent,
                                descriptors: List<SettingsSyncSubcategoryDescriptor>,
                                onCheckBoxChange: () -> Unit) {
    val panel = JPanel(BorderLayout())
    panel.border = JBUI.Borders.empty()
    val checkboxList = CheckBoxList<SettingsSyncSubcategoryDescriptor> { i: Int, isSelected: Boolean ->
      descriptors[i].isSelected = isSelected
      onCheckBoxChange()
    }
    descriptors.forEach { checkboxList.addItem(it, it.name, it.isSelected) }
    val scrollPane = JBScrollPane(checkboxList)
    panel.add(scrollPane, BorderLayout.CENTER)
    val chooserBuilder = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, checkboxList)
    chooserBuilder.createPopup().showUnderneathOf(owner)
  }
}