// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.training

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.util.IconLoader
import training.ui.welcomeScreen.OnboardingLessonPromoter
import javax.swing.Icon
import javax.swing.JPanel

internal class PyOnboardingTourPromoter : OnboardingLessonPromoter("python.onboarding", "Python") {
  override fun promoImage(): Icon = IconLoader.getIcon("img/pycharm-onboarding-tour.png", PyOnboardingTourPromoter::class.java)

  override fun getPromotionForInitialState(): JPanel? {
    if (ApplicationNamesInfo.getInstance().fullProductNameWithEdition.equals("PyCharm Edu")) {
      return null
    }
    return super.getPromotionForInitialState()
  }
}