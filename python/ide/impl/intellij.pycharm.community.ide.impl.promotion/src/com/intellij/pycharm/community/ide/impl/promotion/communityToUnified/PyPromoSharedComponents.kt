// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.promotion.communityToUnified

import com.intellij.ide.BrowserUtil
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.intellij.pycharm.community.ide.impl.promotion.icons.PycharmCommunityIdeImplPromotionIcons
import com.intellij.ui.JBColor
import java.awt.Color

internal object PyPromoSharedComponents {

  val popUpImg = PycharmCommunityIdeImplPromotionIcons.Popup_img
  val infoFontColor = JBColor(Color.decode("#6C707E"), Color.decode("#A8ADBD"))

  // URLs used by promo-related messages
  const val LEARN_MORE_URL: String = "https://blog.jetbrains.com/pycharm/2025/04/unified-pycharm/"

  // Actions
  val learnMoreBrowserAction = { BrowserUtil.browse(LEARN_MORE_URL) }

  // Localized strings from PyCharmCommunityCustomizationBundle
  val mainText = PyCharmCommunityCustomizationBundle.message("promotion.update.to.unified.info.text")
  val learnMore = PyCharmCommunityCustomizationBundle.message("promotion.update.to.unified.learn.more.hyper.link")
  val updateNow = PyCharmCommunityCustomizationBundle.message("promotion.update.to.unified.update.now.button.label")
  val skip = PyCharmCommunityCustomizationBundle.message("promotion.update.to.unified.skip.button.label")
  val headerTitle = PyCharmCommunityCustomizationBundle.message("promotion.update.to.unified.banner.header.title")
}