package com.intellij.searchEverywhereMl.ranking.features.statistician

import com.intellij.ide.ui.RegistryBooleanOptionDescriptor
import com.intellij.ide.ui.RegistryTextOptionDescriptor
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.ide.util.gotoByName.GotoActionModel.ActionWrapper
import com.intellij.ide.util.gotoByName.GotoActionModel.MatchedValue
import com.intellij.openapi.actionSystem.ActionManager

private class SearchEverywhereActionStatistician : SearchEverywhereStatistician<MatchedValue>(MatchedValue::class.java) {
  override fun getValue(element: MatchedValue, location: String) = when (val value = element.value) {
    is ActionWrapper -> getValueForAction(value)
    is OptionDescription -> value.hit ?: value.option
    else -> null
  }

  private fun getValueForAction(action: ActionWrapper) = ActionManager.getInstance().getId(action.action) ?: action.action.javaClass.name

  override fun getContext(element: MatchedValue) = when (val value = element.value) {
    is ActionWrapper -> getContextForAction(value)
    is OptionDescription -> getContextForOption(value)
    else -> null
  }?.let { context -> "$contextPrefix#$context" }

  private fun getContextForAction(action: ActionWrapper): String = action.groupMapping
                                                                     ?.firstGroup
                                                                     ?.mapNotNull { ActionManager.getInstance().getId(it) }
                                                                     ?.joinToString("|")
                                                                   ?: "action"

  private fun getContextForOption(option: OptionDescription): String {
    return if (option is RegistryBooleanOptionDescriptor || option is RegistryTextOptionDescriptor) {
      "registry"
    }
    else {
      option.configurableId ?: option.groupName ?: "option"
    }
  }
}