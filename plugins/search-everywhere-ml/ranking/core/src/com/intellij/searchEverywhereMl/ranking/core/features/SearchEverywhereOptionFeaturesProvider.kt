// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.searchEverywhereMl.ranking.core.features

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.ide.actions.searcheverywhere.TopHitSEContributor
import com.intellij.ide.ui.RegistryBooleanOptionDescriptor
import com.intellij.ide.ui.RegistryTextOptionDescriptor
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereGeneralActionFeaturesProvider.Fields.IS_ENABLED
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereOptionFeaturesProvider.Fields.FROM_CONFIGURABLE
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereOptionFeaturesProvider.Fields.IS_BOOLEAN_OPTION
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereOptionFeaturesProvider.Fields.IS_NOT_DEFAULT
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereOptionFeaturesProvider.Fields.IS_OPTION
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereOptionFeaturesProvider.Fields.IS_REGISTRY_OPTION

internal class SearchEverywhereOptionFeaturesProvider :
  SearchEverywhereElementFeaturesProvider(ActionSearchEverywhereContributor::class.java, TopHitSEContributor::class.java) {
  object Fields {
    internal val IS_OPTION = EventFields.Boolean("isOption")
    internal val IS_BOOLEAN_OPTION = EventFields.Boolean("isBooleanOption")
    internal val IS_REGISTRY_OPTION = EventFields.Boolean("isRegistryOption")
    internal val IS_NOT_DEFAULT = EventFields.Boolean("isNotDefault")
    internal val FROM_CONFIGURABLE = EventFields.Boolean("fromConfigurable")
  }

  override fun getFeaturesDeclarations(): List<EventField<*>> {
    return listOf(IS_OPTION, IS_BOOLEAN_OPTION, IS_REGISTRY_OPTION, IS_NOT_DEFAULT, FROM_CONFIGURABLE)
  }

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: FeaturesProviderCache?,
                                  correction: SearchEverywhereSpellCheckResult): List<EventPair<*>> {
    val value = if (element is GotoActionModel.MatchedValue) element.value else element
    val optionDescription = value as? OptionDescription ?: return emptyList()

    return buildList {
      add(IS_OPTION.with(true))
      addIfTrue(FROM_CONFIGURABLE, StringUtil.isNotEmpty(optionDescription.configurableId))
      addIfTrue(IS_BOOLEAN_OPTION, optionDescription is BooleanOptionDescription)
      if (optionDescription is BooleanOptionDescription) {
        add(IS_ENABLED.with(optionDescription.isOptionEnabled))
      }

      if (optionDescription is RegistryTextOptionDescriptor) {
        add(IS_REGISTRY_OPTION.with(true))
        add(IS_NOT_DEFAULT.with(optionDescription.hasChanged()))
      }
      else if (optionDescription is RegistryBooleanOptionDescriptor) {
        add(IS_REGISTRY_OPTION.with(true))
        add(IS_NOT_DEFAULT.with(optionDescription.hasChanged()))
      }
    }
  }
}