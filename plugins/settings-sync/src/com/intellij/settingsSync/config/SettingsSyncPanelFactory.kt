package com.intellij.settingsSync.config

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.settingsSync.*
import com.intellij.settingsSync.SettingsSyncBundle.message
import com.intellij.ui.CheckBoxList
import com.intellij.ui.CheckBoxListListener
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ThreeStateCheckBox
import com.intellij.util.ui.ThreeStateCheckBox.State
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

internal object SettingsSyncPanelFactory {
  fun createCombinedSyncSettingsPanel(
    syncLabel: @Nls String,
    syncSettings: SettingsSyncState,
    syncScopeSettings: SettingsSyncLocalState,
  ): DialogPanel {
    val categoriesPanel = createSyncCategoriesPanel(syncLabel, syncSettings)
    val syncScopePanel = createSyncScopePanel(syncScopeSettings)

    return panel {
      row {
        cell(categoriesPanel)
          .onApply(categoriesPanel::apply)
          .onReset(categoriesPanel::reset)
          .onIsModified(categoriesPanel::isModified)
      }

      row {
        cell(syncScopePanel)
          .onApply(syncScopePanel::apply)
          .onReset(syncScopePanel::reset)
          .onIsModified(syncScopePanel::isModified)
      }
      onApply {
        SettingsSyncLocalSettings.getInstance().applyFromState(syncScopeSettings)
        SettingsSyncSettings.getInstance().applyFromState(syncSettings)
      }
    }
  }

  private fun createSyncScopePanel(state: SettingsSyncLocalState): DialogPanel {
    return panel {
      row {
        topGap(TopGap.MEDIUM)
        label(message("settings.cross.product.sync"))
      }
      buttonsGroup(indent = true) {
        row {
          val productName = ApplicationNamesInfo.getInstance().fullProductName
          radioButton(message("settings.cross.product.sync.choice.only.this.product", productName), false)
        }
        row {
          radioButton(message("settings.cross.product.sync.choice.all.products"), true)
        }
      }.bind(state::isCrossIdeSyncEnabled)
    }
  }

  private fun createSyncCategoriesPanel(syncLabel: @Nls String, state: SettingsSyncState): DialogPanel {
    return panel {
      row {
        label(syncLabel)
      }
      val categoryHolders = SyncCategoryHolder.createAllForState(state)
      for (holder in categoryHolders) {
        indent {
          row {
            if (holder.secondaryGroup == null) {
              checkBox(
                holder.name
              )
                .bindSelected(holder::isSynchronized)
                .onReset { holder.reset() }
                .onApply { holder.apply() }
                .onIsModified { holder.isModified() }
              comment(holder.description)
            }
            else {
              val topCheckBox = ThreeStateCheckBox(holder.name)
              topCheckBox.isThirdStateEnabled = false
              cell(topCheckBox)
                .onReset {
                  holder.reset()
                  topCheckBox.state = getGroupState(holder)
                }
                .onApply {
                  holder.isSynchronized = topCheckBox.state != State.NOT_SELECTED
                  holder.apply()
                }
                .onIsModified { holder.isModified() }
              val c = comment(holder.description).visible(!holder.description.isEmpty())
              val subcategoryLink = configureLink(holder.secondaryGroup!!, c.component.font.size2D) {
                topCheckBox.state = getGroupState(holder)
                holder.isSynchronized = topCheckBox.state != State.NOT_SELECTED
              }
              cell(subcategoryLink)
                .visible(holder.secondaryGroup!!.getDescriptors().size > 1 || !holder.secondaryGroup!!.isComplete())
              topCheckBox.addActionListener {
                holder.isSynchronized = topCheckBox.state != State.NOT_SELECTED
                holder.secondaryGroup!!.getDescriptors().forEach {
                  it.isSelected = holder.isSynchronized
                }
                subcategoryLink.isEnabled = holder.secondaryGroup!!.isComplete() || holder.isSynchronized
              }
            }

          }
        }
      }
    }
  }

  private fun getGroupState(descriptor: SyncCategoryHolder): State {
    val group = descriptor.secondaryGroup
    if (group == null) {
      return if (descriptor.isSynchronized) State.SELECTED else State.NOT_SELECTED
    }
    if (!group.isComplete() && !descriptor.isSynchronized) return State.NOT_SELECTED
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

  private fun configureLink(group: SyncSubcategoryGroup,
                            fontSize: Float,
                            onCheckBoxChange: () -> Unit): JComponent {
    val actionLink = ActionLink(message("subcategory.config.link")) {}
    actionLink.withFont(actionLink.font.deriveFont(fontSize))
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
    val checkboxList = PluginsCheckboxList(descriptors) { i: Int, isSelected: Boolean ->
      descriptors[i].isSelected = isSelected
      onCheckBoxChange()
    }
    descriptors.forEach { checkboxList.addItem(it, it.name, it.isSelected) }
    val scrollPane = JBScrollPane(checkboxList)
    panel.add(scrollPane, BorderLayout.CENTER)
    scrollPane.border = JBUI.Borders.empty(5)
    val chooserBuilder = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, checkboxList)
    chooserBuilder.createPopup().showUnderneathOf(owner)
  }

  private class PluginsCheckboxList(
    val descriptors: List<SettingsSyncSubcategoryDescriptor>,
    listener : CheckBoxListListener) : CheckBoxList<SettingsSyncSubcategoryDescriptor>(listener) {

    override fun adjustRendering(rootComponent: JComponent,
                                 checkBox: JCheckBox?,
                                 index: Int,
                                 selected: Boolean,
                                 hasFocus: Boolean): JComponent {
      if (descriptors[index].isSubGroupEnd) {
        val itemWrapper = JPanel()
        itemWrapper.layout = BoxLayout(itemWrapper, BoxLayout.Y_AXIS)
        itemWrapper.add(rootComponent)
        itemWrapper.add(SeparatorComponent(5, JBUI.CurrentTheme.Popup.separatorColor(), null))
        return itemWrapper
      }
      return rootComponent
    }
  }
}