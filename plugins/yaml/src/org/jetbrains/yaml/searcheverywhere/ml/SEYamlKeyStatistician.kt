// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.ml.features.statistician.SearchEverywhereStatistician
import com.intellij.psi.statistics.StatisticsInfo
import org.jetbrains.yaml.navigation.YAMLKeyNavigationItem

private class SEYamlKeyStatistician : SearchEverywhereStatistician<YAMLKeyNavigationItem>(YAMLKeyNavigationItem::class.java) {
  override fun getContext(element: YAMLKeyNavigationItem): String? {
    return element.presentation.locationString
  }

  override fun serializeElement(element: YAMLKeyNavigationItem, location: String): StatisticsInfo? {
    val value = element.name
    val context = getContext(element) ?: return null

    return StatisticsInfo(context, value)
  }
}