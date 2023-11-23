// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.training

import com.intellij.openapi.util.IconLoader
import com.intellij.util.PlatformUtils
import com.jetbrains.python.ift.PythonLessonsBundle
import training.ui.welcomeScreen.OnboardingLessonPromoter
import javax.swing.Icon

internal class PyOnboardingTourPromoter : OnboardingLessonPromoter(
  "python.onboarding", "Python", PythonLessonsBundle.message("python.onboarding.lesson.name")
) {
  override val promoImage: Icon
    get() = IconLoader.getIcon("img/pycharm-onboarding-tour.png", PyOnboardingTourPromoter::class.java.classLoader)

  override fun canCreatePromo(isEmptyState: Boolean): Boolean =
    super.canCreatePromo(isEmptyState) && (PlatformUtils.isPyCharmCommunity() || PlatformUtils.isPyCharmPro())
}