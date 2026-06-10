// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.welcomeScreen

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.InitialConfigImportState
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.platform.ide.nonModalWelcomeScreen.NON_MODAL_WELCOME_SCREEN_SETTING_ID
import com.intellij.ui.LicensingFacade
import com.jetbrains.python.sdk.legacy.PythonSdkUtil

/**
 * Turns the non-modal welcome screen on by default for free-tier (Pro disabled) and brand-new
 * PyCharm users. [enableOnce] switches it on at most once and stores [FREE_MODE_OVERRIDE_DONE], so a
 * user who later turns it off is never overridden.
 *
 * The tier can't be read at startup: a paying user may simply have no license loaded yet (it arrives
 * a few seconds later), and until then they look exactly like a free-tier user. So we enable the
 * setting from [licenseStateChanged], which the license manager fires only once the license has
 * actually been loaded and [PythonSdkUtil.isFreeTier] is meaningful. A new user does not depend on
 * the license, so [appStarted] handles that case immediately.
 */
internal class PyWelcomeScreenFreeModeTracker : AppLifecycleListener, LicensingFacade.LicenseStateListener {
  override fun appStarted() {
    if (InitialConfigImportState.isNewUser()) {
      enableOnce()
    }
  }

  override fun licenseStateChanged(newState: LicensingFacade?) {
    if (PythonSdkUtil.isFreeTier()) {
      enableOnce()
    }
  }

  private fun enableOnce() {
    val properties = PropertiesComponent.getInstance()
    if (properties.getBoolean(FREE_MODE_OVERRIDE_DONE, false)) {
      return
    }
    properties.setValue(FREE_MODE_OVERRIDE_DONE, true)
    AdvancedSettings.setBoolean(NON_MODAL_WELCOME_SCREEN_SETTING_ID, true)
  }

  companion object {
    private const val FREE_MODE_OVERRIDE_DONE = "pycharm.welcome.free.mode.override"
  }
}
