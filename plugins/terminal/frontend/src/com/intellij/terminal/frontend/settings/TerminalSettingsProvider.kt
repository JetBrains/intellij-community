package com.intellij.terminal.frontend.settings

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

/**
 * Allows providing a UI component to the "Settings | Tools | Terminal" page.
 *
 * **Note that it is expected that implementation of this interface should be located in the frontend part of the plugin**.
 *
 * The common use case is to provide a way to enable/disable a feature implemented in the plugin.
 * For example, by adding a UI control with a state that is bound to the value in the plugin's [com.intellij.openapi.components.PersistentStateComponent].
 * Though, usually, the state of the option is required to be used on the backend part of the plugin as well.
 * To make it work, consider configuring settings synchronization using [com.intellij.ide.settings.RemoteSettingInfoProvider]
 * to ensure that option state is passed to the backend part of the plugin when it is changed in the settings (frontend part).
 *
 * Register the implementation as `org.jetbrains.plugins.terminal.terminalSettingsProvider`
 * extension in the XML configuration of your frontend module of the plugin.
 */
@ApiStatus.Experimental
@ApiStatus.OverrideOnly
interface TerminalSettingsProvider {
  /**
   * @return a new instance of [UnnamedConfigurable] to be added at the bottom of the Terminal settings page
   */
  @RequiresEdt(generateAssertion = false)
  fun createConfigurable(project: Project): UnnamedConfigurable?

  companion object {
    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<TerminalSettingsProvider> =
      ExtensionPointName("org.jetbrains.plugins.terminal.terminalSettingsProvider")
  }
}