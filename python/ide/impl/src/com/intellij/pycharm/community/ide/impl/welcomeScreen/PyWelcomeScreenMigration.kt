// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.welcomeScreen

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.platform.ide.nonModalWelcomeScreen.NON_MODAL_WELCOME_SCREEN_SETTING_ID

/** Enables the non-modal welcome screen once for all users. Preserves their subsequent changes in Settings. */
internal class PyWelcomeScreenMigration : AppLifecycleListener {
  override fun appStarted() {
    val properties = PropertiesComponent.getInstance()
    if (properties.getBoolean(MIGRATION_DONE, false)) {
      return
    }

    AdvancedSettings.setBoolean(NON_MODAL_WELCOME_SCREEN_SETTING_ID, true)
    properties.setValue(MIGRATION_DONE, true)
  }

  companion object {
    private const val MIGRATION_DONE = "pycharm.welcome.all.users.override"
  }
}
