// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere.ml.yaml.features.statistician

import com.intellij.ide.actions.searcheverywhere.ml.features.statistician.SearchEverywhereStatistician
import org.jetbrains.yaml.navigation.YAMLKeyNavigationItem

private class SEYamlKeyStatistician : SearchEverywhereStatistician<YAMLKeyNavigationItem>(YAMLKeyNavigationItem::class.java) {
  override fun getContext(element: YAMLKeyNavigationItem) = element.presentation.locationString

  override fun getValue(element: YAMLKeyNavigationItem, location: String) = element.name
}