// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.platform.ide.nonModalWelcomeScreen.NON_MODAL_WELCOME_SCREEN_SETTING_ID
import com.intellij.pycharm.community.ide.impl.welcomeScreen.PyWelcomeScreenMigration
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
internal class PyWelcomeScreenMigrationTest {
  private val properties get() = PropertiesComponent.getInstance()
  private var savedProperties: Map<String, String?> = emptyMap()
  private var wasEnabled = false

  @BeforeEach
  fun setUp() {
    savedProperties = PROPERTY_KEYS.associateWith { properties.getValue(it) }
    PROPERTY_KEYS.forEach { properties.unsetValue(it) }
    wasEnabled = AdvancedSettings.getBoolean(NON_MODAL_WELCOME_SCREEN_SETTING_ID)
  }

  @AfterEach
  fun tearDown() {
    savedProperties.forEach { (key, value) -> properties.setValue(key, value) }
    AdvancedSettings.setBoolean(NON_MODAL_WELCOME_SCREEN_SETTING_ID, wasEnabled)
  }

  @Test
  fun `enables the screen regardless of previous migrations`() {
    for (previousMigrations in 0..7) {
      properties.unsetValue(MIGRATION_DONE)
      properties.setValue("pycharm.welcome.overridden", previousMigrations and 1 != 0)
      properties.setValue("pycharm.welcome.free.mode.override", previousMigrations and 2 != 0)
      properties.setValue("pycharm.welcome.non.modal.baseline.applied", previousMigrations and 4 != 0)
      AdvancedSettings.setBoolean(NON_MODAL_WELCOME_SCREEN_SETTING_ID, false)

      PyWelcomeScreenMigration().appStarted()

      assertTrue(AdvancedSettings.getBoolean(NON_MODAL_WELCOME_SCREEN_SETTING_ID), "Previous migrations: $previousMigrations")
      assertTrue(properties.getBoolean(MIGRATION_DONE))
    }
  }

  @Test
  fun `preserves an opt-out after migration on subsequent starts`() {
    PyWelcomeScreenMigration().appStarted()
    AdvancedSettings.setBoolean(NON_MODAL_WELCOME_SCREEN_SETTING_ID, false)

    repeat(2) { PyWelcomeScreenMigration().appStarted() }

    assertFalse(AdvancedSettings.getBoolean(NON_MODAL_WELCOME_SCREEN_SETTING_ID))
  }

  @Test
  fun `completes migration when the screen is already enabled`() {
    AdvancedSettings.setBoolean(NON_MODAL_WELCOME_SCREEN_SETTING_ID, true)

    PyWelcomeScreenMigration().appStarted()

    assertTrue(AdvancedSettings.getBoolean(NON_MODAL_WELCOME_SCREEN_SETTING_ID))
    assertTrue(properties.getBoolean(MIGRATION_DONE))
  }

  companion object {
    private const val MIGRATION_DONE = "pycharm.welcome.all.users.override"
    private val PROPERTY_KEYS = listOf(
      MIGRATION_DONE,
      "pycharm.welcome.overridden",
      "pycharm.welcome.free.mode.override",
      "pycharm.welcome.non.modal.baseline.applied",
    )
  }
}
