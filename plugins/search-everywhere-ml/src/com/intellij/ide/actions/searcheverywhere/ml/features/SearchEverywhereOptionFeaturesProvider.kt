// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.ui.RegistryBooleanOptionDescriptor
import com.intellij.ide.ui.RegistryTextOptionDescriptor
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.openapi.util.text.StringUtil

internal class SearchEverywhereOptionFeaturesProvider : SearchEverywhereBaseActionFeaturesProvider() {
  companion object {
    private const val IS_OPTION = "isOption"
    private const val IS_BOOLEAN_OPTION = "isBooleanOption"
    private const val IS_REGISTRY_OPTION = "isRegistryOption"
    private const val IS_NOT_DEFAULT = "isNotDefault"
    private const val FROM_CONFIGURABLE = "fromConfigurable"
  }

  override fun getFeatures(data: MutableMap<String, Any>, currentTime: Long, matchedValue: GotoActionModel.MatchedValue): Map<String, Any> {
    val optionDescription = matchedValue.value as? OptionDescription
    data[IS_OPTION] = optionDescription != null

    if (optionDescription == null) {
      return data
    }

    addIfTrue(data, FROM_CONFIGURABLE, StringUtil.isNotEmpty(optionDescription.configurableId))
    addIfTrue(data, IS_BOOLEAN_OPTION, optionDescription is BooleanOptionDescription)
    if (optionDescription is BooleanOptionDescription) {
      data[IS_ENABLED] = optionDescription.isOptionEnabled
    }

    if (optionDescription is RegistryTextOptionDescriptor) {
      data[IS_REGISTRY_OPTION] = true
      data[IS_NOT_DEFAULT] = optionDescription.hasChanged()
    }
    else if (optionDescription is RegistryBooleanOptionDescriptor) {
      data[IS_REGISTRY_OPTION] = true
      data[IS_NOT_DEFAULT] = optionDescription.hasChanged()
    }
    return data
  }
}