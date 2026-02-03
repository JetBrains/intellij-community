// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.welcomeScreen

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.InitialConfigImportState
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.platform.ide.nonModalWelcomeScreen.NON_MODAL_WELCOME_SCREEN_SETTING_ID

class PyWelcomeScreenNewUsersTracker : AppLifecycleListener {
  override fun appStarted() {
    // Dirty temporary solution. Will be removed once we test metrics for the new non-modal welcome screen.
    // We cannot override advanced settings per IDE, so we set it true for new users and false to others.
    // And we do it only once.
    if (PropertiesComponent.getInstance().getBoolean(IS_ADVANCED_SETTING_WAS_OVERRIDDEN, false)) {
      return
    }
    PropertiesComponent.getInstance().setValue(IS_ADVANCED_SETTING_WAS_OVERRIDDEN, true)
    AdvancedSettings.setBoolean(NON_MODAL_WELCOME_SCREEN_SETTING_ID, InitialConfigImportState.isNewUser())
  }

  companion object {
    private const val IS_ADVANCED_SETTING_WAS_OVERRIDDEN = "pycharm.welcome.overridden"
  }
}
