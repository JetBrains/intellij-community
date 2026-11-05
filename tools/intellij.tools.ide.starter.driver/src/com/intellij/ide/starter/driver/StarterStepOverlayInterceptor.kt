package com.intellij.ide.starter.driver

import com.intellij.driver.sdk.ui.StepOverlayInterceptor
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.driverInfoOverlaysEnabled
import com.intellij.ide.starter.runner.CurrentTestMethod

/** The package of the UI tests. The overlays change what a performance test measures, so the other tests show none. */
private const val UI_TESTS_PACKAGE = "com.intellij.driver.tests."

/**
 * Shows the step overlays in the UI tests of [UI_TESTS_PACKAGE].
 * The `DRIVER_INFO_OVERLAYS_ENABLED` setting of [ConfigurationStorage] turns them on or off for every test.
 */
internal class StarterStepOverlayInterceptor : StepOverlayInterceptor() {
  override fun isEnabled(): Boolean =
    ConfigurationStorage.driverInfoOverlaysEnabled() ?: (CurrentTestMethod.get()?.clazz?.startsWith(UI_TESTS_PACKAGE) == true)
}
