package com.intellij.terminal.frontend.session

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.provider.LocalEelDescriptor
import io.github.ketraterm.shell.launch.TerminalShellLaunch
import io.github.ketraterm.workspace.TerminalProfile
import java.nio.file.Path
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.TerminalEmulatorType

/**
 * Prepares backend shell hooks for an IntelliJ-owned process.
 */
internal class ReworkedLocalTerminalRunner(project: Project) : LocalTerminalDirectRunner(project) {
  var shellLaunch: TerminalShellLaunch? = null
    private set

  override fun configureShellIntegration(options: ShellStartupOptions): ShellStartupOptions {
    if ((options.emulatorType ?: TerminalEmulatorType.default) != TerminalEmulatorType.KetraTerm ||
        options.eelDescriptorNotNull != LocalEelDescriptor) {
      return super.configureShellIntegration(options)
    }
    val launch = TerminalShellLaunch.prepare(
      TerminalProfile("intellij", "IntelliJ", requireNotNull(options.shellCommand), options.envVariables),
      scriptDirectory = Path.of(PathManager.getSystemPath(), "ketraterm", "shell-scripts"),
    )
    shellLaunch = launch
    return options.builder().shellCommand(launch.profile.command).envVariables(launch.profile.environment).build()
  }

  override fun isGenTwoTerminalEnabled(): Boolean {
    return true
  }
}
