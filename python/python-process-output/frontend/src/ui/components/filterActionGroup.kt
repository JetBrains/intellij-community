package com.intellij.python.processOutput.frontend.ui.components

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.python.processOutput.frontend.Filter
import com.intellij.python.processOutput.frontend.FilterActionGroupState
import com.intellij.python.processOutput.frontend.FilterItem
import org.jetbrains.annotations.Nls

internal inline fun <TFilter, reified TItem> filterActionGroup(
  name: @Nls String,
  state: FilterActionGroupState<TFilter, TItem>,
  crossinline onFilterItemToggled: (filterItem: TItem, enabled: Boolean) -> Unit,
): ActionGroup where TItem : Enum<TItem>, TItem : FilterItem, TFilter : Filter<TItem> {
  val group = DefaultActionGroup(name, null, AllIcons.Actions.Show)
  group.isPopup = true

  for (entry in enumValues<TItem>()) {
    group.add(
      object : DumbAwareToggleAction(entry.title) {
        override fun isSelected(e: AnActionEvent): Boolean =
          state[entry]

        override fun setSelected(e: AnActionEvent, value: Boolean) {
          state[entry] = value
          onFilterItemToggled(entry, value)
        }

        override fun getActionUpdateThread(): ActionUpdateThread {
          return ActionUpdateThread.BGT
        }
      }
    )
  }

  return group
}
