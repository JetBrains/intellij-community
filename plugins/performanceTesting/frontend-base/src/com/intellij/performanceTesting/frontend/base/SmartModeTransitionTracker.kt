// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.frontend.base

import com.intellij.internal.statistic.SmartModeTransitionPhase
import com.intellij.internal.statistic.SmartModeTransitionPhaseListener

/**
 * Tracks the IJ Light -> smart mode transition, so tests can wait for the `frontend.split` plugin modules to be
 * loaded before driving classes from them, and for the transition itself (including the trust hand-over to the
 * backend) to finish before asserting on its outcome (see `IjLightBackgroundRun.upgradeToSmartMode`). Lives in a
 * content module that is loadable in the light product mode, unlike the classes the tests wait for.
 */
internal class SmartModeTransitionTracker : SmartModeTransitionPhaseListener {
  override fun phaseFinished(phase: SmartModeTransitionPhase) {
    if (phase == SmartModeTransitionPhase.PLUGINS_LOADED) {
      smartModePluginsLoaded = true
    }
  }

  override fun transitionFinished(reachedSmart: Boolean) {
    if (reachedSmart) {
      transitionFinished = true
    }
  }

  companion object {
    @Volatile
    private var smartModePluginsLoaded: Boolean = false

    @Volatile
    private var transitionFinished: Boolean = false

    /** True once the transition finished loading the `frontend.split` plugin modules. Called through the Driver. */
    @JvmStatic
    fun isSmartModePluginsLoaded(): Boolean = smartModePluginsLoaded

    /** True once the light session finished handing its trust decision, and the rest of the upgrade, to the backend. */
    @JvmStatic
    fun isTransitionFinished(): Boolean = transitionFinished
  }
}
