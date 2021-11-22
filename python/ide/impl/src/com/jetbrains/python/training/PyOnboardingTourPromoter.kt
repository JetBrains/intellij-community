// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.training

import com.intellij.lang.LanguageExtensionPoint
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.IconLoader
import training.lang.LangSupport
import training.ui.showOnboardingFeedbackNotification
import training.ui.welcomeScreen.OnboardingLessonPromoter
import javax.swing.Icon
import javax.swing.JPanel

internal class PyOnboardingTourPromoter : OnboardingLessonPromoter("python.onboarding", "Python") {
  override fun promoImage(): Icon = IconLoader.getIcon("img/pycharm-onboarding-tour.png", PyOnboardingTourPromoter::class.java)

  override fun getPromotionForInitialState(): JPanel? {
    if (ApplicationNamesInfo.getInstance().fullProductNameWithEdition.equals("PyCharm Edu")) {
      return null
    }
    // A bit hacky way to schedule the onboarding feedback informer after the lesson was closed

    val pythonLanguageSupport = ExtensionPointName<LanguageExtensionPoint<LangSupport>>(
      LangSupport.EP_NAME).extensionList.singleOrNull { it.language == "Python" }?.instance

    if (pythonLanguageSupport != null) {
      val onboardingFeedbackData = pythonLanguageSupport.onboardingFeedbackData
      if (onboardingFeedbackData != null) {
        invokeLater {
          pythonLanguageSupport.onboardingFeedbackData = null
          showOnboardingFeedbackNotification(null, onboardingFeedbackData)
        }
      }
    }
    return super.getPromotionForInitialState()
  }
}