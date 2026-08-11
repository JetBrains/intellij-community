// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.welcomeScreen

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.InitialConfigImportState
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsImpl
import com.intellij.platform.ide.nonModalWelcomeScreen.NON_MODAL_WELCOME_SCREEN_SETTING_ID
import com.intellij.ui.LicensingFacade
import com.jetbrains.python.sdk.legacy.PythonSdkUtil

/**
 * Picks the welcome screen default once per user: non-modal for new and free-mode users, classic modal
 * for paying users. Runs once ([MIGRATION_DONE]); after that the user's own choice stands.
 *
 * Why this is fiddly: the setting defaults to `true` and can't be defaulted per IDE, so we must write
 * the value. And we can't tell paid from free at startup — for a paying user the Ultimate plugin stays
 * disabled (so [PythonSdkUtil.isFreeTier] reads `true`) until the license loads a bit later.
 *
 * The rule that keeps a paying user off the non-modal screen: only ever conclude "paying" from a
 * positive signal (plugin enabled, or a license in [LicensingFacade]); when still unsure, default to
 * modal; and conclude "free" only after the license had a full session to show up.
 */
internal class PyWelcomeScreenFreeModeTracker : AppLifecycleListener, LicensingFacade.LicenseStateListener {
  override fun appStarted() {
    val properties = PropertiesComponent.getInstance()
    if (properties.getBoolean(MIGRATION_DONE, false)) {
      return
    }

    // PY-82074 already turned the non-modal screen on for this user and it's still on: keep it.
    if (keepNonModalEnabledByPy82074(properties)) {
      return
    }

    // New user: non-modal. Doesn't depend on the license, so decide immediately.
    if (InitialConfigImportState.isNewUser()) {
      finish(enabled = true)
      return
    }

    // Plugin enabled => paying (can't be true for a free user) => modal.
    if (!PythonSdkUtil.isFreeTier()) {
      finish(enabled = false)
      return
    }

    // Plugin disabled: either free, or paying with the license not loaded yet. A license already in the
    // facade settles it as paying => modal.
    val facade = LicensingFacade.getInstance()
    if (hasPlatformLicense(facade)) {
      finish(enabled = false)
      return
    }

    // No license yet. If a previous launch already gave the license a full session to appear and it
    // still hasn't, this is genuinely free => non-modal. (facade == null: not reported yet, keep waiting.)
    if (properties.getBoolean(MODAL_BASELINE_APPLIED, false)) {
      if (facade != null) {
        finish(enabled = true)
      }
      return
    }

    // First launch, tier still unknown: show modal for now and wait for the license, both this session
    // (licenseStateChanged) and on the next launch (the branch above).
    applyModalBaseline()
    properties.setValue(MODAL_BASELINE_APPLIED, true)
    baselineAppliedThisSession = true
  }

  override fun licenseStateChanged(newState: LicensingFacade?) {
    // Fires synchronously from LicensingFacade.setInstance(), which can run during early startup before
    // the app component store is initialized; touching PropertiesComponent/AdvancedSettings then throws
    // and aborts startup. The facade keeps its state, so appStarted() and any later fire (after the store
    // is ready) still make the decision.
    if (!LoadingState.CONFIGURATION_STORE_INITIALIZED.isOccurred) {
      return
    }
    val properties = PropertiesComponent.getInstance()
    if (properties.getBoolean(MIGRATION_DONE, false)) return
    if (InitialConfigImportState.isNewUser()) return

    // PY-82074 already turned the non-modal screen on for this user and it's still on: keep it,
    // even if a paying license only settles now (mid-session).
    if (keepNonModalEnabledByPy82074(properties)) return

    // License appeared => paying => modal.
    if (hasPlatformLicense(newState)) {
      finish(enabled = false)
      return
    }

    // Still no license. Conclude "free" only if a *previous* launch already biased us to modal, so a
    // paying user whose license is just slow this session isn't misread as free. baselineAppliedThisSession
    // excludes the launch that set that flag.
    if (newState != null
        && !baselineAppliedThisSession
        && properties.getBoolean(MODAL_BASELINE_APPLIED, false)
        && PythonSdkUtil.isFreeTier()
    ) {
      finish(enabled = true)
    }
  }

  private fun finish(enabled: Boolean) {
    AdvancedSettings.setBoolean(NON_MODAL_WELCOME_SCREEN_SETTING_ID, enabled)
    PropertiesComponent.getInstance().setValue(MIGRATION_DONE, true)
  }

  /**
   * PY-82074 explicitly enabled the non-modal screen for this user (they were a new user then) and it
   * is still on. Keep it — don't force them back to modal now just because they've since started
   * paying. Requires the explicit PY-82074 flag, not merely the setting's value, so the platform's
   * default `true` isn't mistaken for a real choice.
   */
  private fun keepNonModalEnabledByPy82074(properties: PropertiesComponent): Boolean {
    if (properties.getBoolean(IS_ADVANCED_SETTING_WAS_OVERRIDDEN, false)
        && AdvancedSettings.getBoolean(NON_MODAL_WELCOME_SCREEN_SETTING_ID)
    ) {
      finish(enabled = true)
      return true
    }
    return false
  }

  /** Falls back to modal, but only if the user hasn't already chosen a value (don't override that). */
  private fun applyModalBaseline() {
    val settings = AdvancedSettings.getInstance() as? AdvancedSettingsImpl ?: return
    if (!settings.isNonDefault(NON_MODAL_WELCOME_SCREEN_SETTING_ID)) {
      AdvancedSettings.setBoolean(NON_MODAL_WELCOME_SCREEN_SETTING_ID, false)
    }
  }

  /** A platform license means paying or on a trial; free mode has none. */
  private fun hasPlatformLicense(facade: LicensingFacade?): Boolean {
    val productCode = facade?.platformProductCode ?: return false
    return facade.getConfirmationStamp(productCode) != null
  }

  companion object {
    // Reuses the PY-89419 key on purpose: the broken revisions only shipped to EAP/nightly, so released
    // users don't have it and the fixed decision still runs once for them. It differs from PY-82074's
    // `pycharm.welcome.overridden`, so that earlier rollout's users are re-evaluated here — except those
    // who already have the non-modal screen on, which [keepNonModalEnabledByPy82074] preserves.
    private const val MIGRATION_DONE = "pycharm.welcome.free.mode.override"
    private const val MODAL_BASELINE_APPLIED = "pycharm.welcome.non.modal.baseline.applied"

    // PY-82074's one-shot key: set for every user who ran that rollout; new users also got non-modal ON.
    // Used to preserve those already-enabled non-modal screens instead of re-deciding them here.
    private const val IS_ADVANCED_SETTING_WAS_OVERRIDDEN = "pycharm.welcome.overridden"

    // Set only on the launch that applies the modal baseline; lets the next launch (not this one)
    // conclude "free". Static so both listener instances (two topics) share it.
    @Volatile
    private var baselineAppliedThisSession = false
  }
}
