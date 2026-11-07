package com.intellij.ide.starter.driver

import com.intellij.driver.sdk.ui.StepOverlayInterceptor
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.driverInfoOverlaysEnabled
import com.intellij.ide.starter.runner.CurrentTestMethod

/**
 * Shows the step overlays in every test, except a performance test. An overlay changes what such a test measures.
 * The `DRIVER_INFO_OVERLAYS_ENABLED` setting of [ConfigurationStorage] turns them on or off for every test.
 */
internal class StarterStepOverlayInterceptor : StepOverlayInterceptor() {
  override fun isEnabled(): Boolean =
    ConfigurationStorage.driverInfoOverlaysEnabled() ?: (CurrentTestMethod.get()?.isPerformanceTest != true)
}
