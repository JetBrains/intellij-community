// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.RunOnceUtil
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ExperimentalUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.ui.updateFrontendSettingsAndSync

/**
 * Internal logic related to the migration of Experimental 2024 Terminal users to the Reworked Terminal.
 */
@ApiStatus.Internal
object ExperimentalTerminalMigration {
  private const val EXP_ENGINE_OPTION_VISIBLE_REGISTRY = "terminal.new.ui.option.visible"
  private const val SWITCHED_FROM_EXP_TERMINAL_PROPERTY = "terminal.switched.from.exp"
  private const val ENGINE_SWITCH_NOTIFICATION_SHOWN_PROPERTY = "terminal.engine.switch.notification.shown"

  fun migrateTerminalEngineOnce(options: TerminalOptionsProvider) {
    RunOnceUtil.runOnceForApp("TerminalOptionsProvider.TerminalEngineMigration.2026.1") {
      updateFrontendSettingsAndSync(options.coroutineScope) {
        if (options.terminalEngine == TerminalEngine.NEW_TERMINAL) {
          options.terminalEngine = TerminalEngine.REWORKED
          // Ensure that the Experimental Terminal option is still visible
          Registry.get(EXP_ENGINE_OPTION_VISIBLE_REGISTRY).setValue(true)
          PropertiesComponent.getInstance().setValue(SWITCHED_FROM_EXP_TERMINAL_PROPERTY, true)
          thisLogger().info("Switched terminal engine to Reworked")
        }
      }
    }
  }

  /**
   * Whether the Experimental 2024 terminal engine option should be visible to user. In the settings, menus, and other places.
   */
  fun isExpTerminalOptionVisible(): Boolean {
    TerminalOptionsProvider.instance // Ensure that all setting migrations are performed
    return ExperimentalUI.isNewUI() && Registry.`is`(EXP_ENGINE_OPTION_VISIBLE_REGISTRY, false)
  }

  fun shouldShowEngineChangeNotification(): Boolean {
    val properties = PropertiesComponent.getInstance()
    return ExperimentalUI.isNewUI()
           && properties.getBoolean(SWITCHED_FROM_EXP_TERMINAL_PROPERTY, false)
           && !properties.getBoolean(ENGINE_SWITCH_NOTIFICATION_SHOWN_PROPERTY, false)
  }

  fun setEngineChangeNotificationShown() {
    PropertiesComponent.getInstance().setValue(ENGINE_SWITCH_NOTIFICATION_SHOWN_PROPERTY, true)
  }
}