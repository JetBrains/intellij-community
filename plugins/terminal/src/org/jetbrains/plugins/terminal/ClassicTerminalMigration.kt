// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.RunOnceUtil
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.ui.ExperimentalUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.ui.updateFrontendSettingsAndSync

/**
 * Internal logic related to the migration of Classic Terminal users to the Reworked Terminal.
 */
@ApiStatus.Internal
object ClassicTerminalMigration {
  private const val SWITCHED_FROM_CLASSIC_TERMINAL_PROPERTY = "terminal.switched.from.classic"
  private const val ENGINE_SWITCH_NOTIFICATION_SHOWN_PROPERTY = "terminal.classic.engine.switch.notification.shown"
  private const val SWITCH_BACK_FEEDBACK_NOTIFICATION_SHOWN_PROPERTY = "terminal.classic.switch.back.feedback.notification.shown"

  fun migrateTerminalEngineOnce(options: TerminalOptionsProvider) {
    RunOnceUtil.runOnceForApp("TerminalOptionsProvider.ClassicTerminalEngineMigration.2026.3") {
      updateFrontendSettingsAndSync(options.coroutineScope) {
        if (ExperimentalUI.isNewUI() && options.terminalEngine == TerminalEngine.CLASSIC) {
          options.terminalEngine = TerminalEngine.REWORKED
          PropertiesComponent.getInstance().setValue(SWITCHED_FROM_CLASSIC_TERMINAL_PROPERTY, true)
          thisLogger().info("Switched terminal engine to Reworked")

          ClassicTerminalColorsMigration.migrateCustomizedColors()
        }
      }
    }
  }

  fun shouldShowEngineChangeNotification(): Boolean {
    val properties = PropertiesComponent.getInstance()
    return ExperimentalUI.isNewUI()
           && TerminalOptionsProvider.instance.terminalEngine == TerminalEngine.REWORKED
           && properties.getBoolean(SWITCHED_FROM_CLASSIC_TERMINAL_PROPERTY, false)
           && !properties.getBoolean(ENGINE_SWITCH_NOTIFICATION_SHOWN_PROPERTY, false)
           && !properties.getBoolean(SWITCH_BACK_FEEDBACK_NOTIFICATION_SHOWN_PROPERTY, false)
  }

  fun setEngineChangeNotificationShown() {
    PropertiesComponent.getInstance().setValue(ENGINE_SWITCH_NOTIFICATION_SHOWN_PROPERTY, true)
  }

  fun wasSwitchedFromClassicTerminal(): Boolean {
    return PropertiesComponent.getInstance().getBoolean(SWITCHED_FROM_CLASSIC_TERMINAL_PROPERTY, false)
  }

  fun shouldShowSwitchBackFeedbackNotification(): Boolean {
    val properties = PropertiesComponent.getInstance()
    return wasSwitchedFromClassicTerminal()
           && !properties.getBoolean(SWITCH_BACK_FEEDBACK_NOTIFICATION_SHOWN_PROPERTY, false)
  }

  fun setSwitchBackFeedbackNotificationShown() {
    PropertiesComponent.getInstance().setValue(SWITCH_BACK_FEEDBACK_NOTIFICATION_SHOWN_PROPERTY, true)
  }
}
