// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.promo

import com.intellij.icons.AllIcons
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PromoFeatureListItem
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle

val djangoPromoFeatureList: List<PromoFeatureListItem> = listOf(
  PromoFeatureListItem(AllIcons.Actions.ReformatCode, PyCharmCommunityCustomizationBundle.message("feature.django.code")),
  PromoFeatureListItem(AllIcons.FileTypes.Html, PyCharmCommunityCustomizationBundle.message("feature.django.djangoTemplates")),
  PromoFeatureListItem(AllIcons.General.Web, PyCharmCommunityCustomizationBundle.message("feature.django.endpoints"))
)