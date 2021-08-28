// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.ui.search.OptionDescription
import com.intellij.ide.util.gotoByName.GotoActionModel

internal class SearchEverywhereOptionFeaturesProvider : SearchEverywhereBaseActionFeaturesProvider() {
  companion object {
    private const val IS_OPTION = "isOption"
  }

  override fun isElementSupported(element: Any): Boolean {
    return element is GotoActionModel.MatchedValue && element.value is OptionDescription
  }

  override fun getFeatures(data: MutableMap<String, Any>, currentTime: Long, matchedValue: GotoActionModel.MatchedValue): Map<String, Any> {
    val optionDescriptor = matchedValue.value as? OptionDescription
    data[IS_OPTION] = optionDescriptor != null

    if (optionDescriptor == null) {
      // item is an option (OptionDescriptor)
      return data
    }
    return data
  }
}