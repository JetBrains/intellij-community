// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.promo

import com.intellij.icons.AllIcons
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FeaturePromoBundle
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PromoFeatureListItem

val javaScriptPromoFeatureList: List<PromoFeatureListItem> = listOf(
  PromoFeatureListItem(AllIcons.Actions.ReformatCode, FeaturePromoBundle.message("feature.javascript.code")),
  PromoFeatureListItem(AllIcons.Actions.SuggestedRefactoringBulb, FeaturePromoBundle.message("feature.javascript.refactor")),
  @Suppress("DialogTitleCapitalization") // Properly capitalized, React and Vue are names.
  PromoFeatureListItem(AllIcons.FileTypes.UiForm, FeaturePromoBundle.message("feature.javascript.frameworks"))
)