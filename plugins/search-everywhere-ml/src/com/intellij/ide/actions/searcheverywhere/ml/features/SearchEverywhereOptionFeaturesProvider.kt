// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.TopHitSEContributor
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereGeneralActionFeaturesProvider.Companion.IS_ENABLED
import com.intellij.ide.ui.RegistryBooleanOptionDescriptor
import com.intellij.ide.ui.RegistryTextOptionDescriptor
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.util.text.StringUtil

internal class SearchEverywhereOptionFeaturesProvider :
  SearchEverywhereElementFeaturesProvider(ActionSearchEverywhereContributor::class.java, TopHitSEContributor::class.java) {
  companion object {
    internal val IS_OPTION = EventFields.Boolean("isOption")
    internal val IS_BOOLEAN_OPTION = EventFields.Boolean("isBooleanOption")
    internal val IS_REGISTRY_OPTION = EventFields.Boolean("isRegistryOption")
    internal val IS_NOT_DEFAULT = EventFields.Boolean("isNotDefault")
    internal val FROM_CONFIGURABLE = EventFields.Boolean("fromConfigurable")
  }

  override fun getFeaturesDeclarations(): List<EventField<*>> {
    return arrayListOf<EventField<*>>(IS_OPTION, IS_BOOLEAN_OPTION, IS_REGISTRY_OPTION, IS_NOT_DEFAULT, FROM_CONFIGURABLE)
  }

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: FeaturesProviderCache?): List<EventPair<*>> {
    val value = if (element is GotoActionModel.MatchedValue) element.value else element
    val optionDescription = value as? OptionDescription ?: return emptyList()

    val data = arrayListOf<EventPair<*>>()
    data.add(IS_OPTION.with(true))
    addIfTrue(data, FROM_CONFIGURABLE, StringUtil.isNotEmpty(optionDescription.configurableId))
    addIfTrue(data, IS_BOOLEAN_OPTION, optionDescription is BooleanOptionDescription)
    if (optionDescription is BooleanOptionDescription) {
      data.add(IS_ENABLED.with(optionDescription.isOptionEnabled))
    }

    if (optionDescription is RegistryTextOptionDescriptor) {
      data.add(IS_REGISTRY_OPTION.with(true))
      data.add(IS_NOT_DEFAULT.with(optionDescription.hasChanged()))
    }
    else if (optionDescription is RegistryBooleanOptionDescriptor) {
      data.add(IS_REGISTRY_OPTION.with(true))
      data.add(IS_NOT_DEFAULT.with(optionDescription.hasChanged()))
    }
    return data
  }
}