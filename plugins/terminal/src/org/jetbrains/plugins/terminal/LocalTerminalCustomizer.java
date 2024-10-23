// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public abstract class LocalTerminalCustomizer {
  public static final ExtensionPointName<LocalTerminalCustomizer> EP_NAME =
    ExtensionPointName.create("org.jetbrains.plugins.terminal.localTerminalCustomizer");

  /**
   * May alter the command to be run in terminal and/or adjust starting environment
   *
   * @param command original command to run
   * @param envs    mutable map of environment variables
   * @return new command to run. Original {@code command} should be returned if no alterations performed
   * @apiNote terminal starting shell session with user-specified shell. Under the hood we are running shell with custom rcfile, e.g.:
   * {@code /usr/bin/bash --rcfile PATH_TO/bash-integration.bash}. See the {@code bash-integration.bash} script
   * for more information on how to alter the execution process.
   */
  public String[] customizeCommandAndEnvironment(@NotNull Project project,
                                                 @Nullable String workingDirectory,
                                                 @NotNull String[] command,
                                                 @NotNull Map<String, String> envs) {
    return command;
  }

  /**
   * @deprecated use LocalTerminalCustomizer#customizeCommandAndEnvironment(Project, String, String[], Map)
   */
  @Deprecated
  public String[] customizeCommandAndEnvironment(@NotNull Project project,
                                                 @NotNull String[] command,
                                                 @NotNull Map<String, String> envs) {
    return customizeCommandAndEnvironment(project, null, command, envs);
  }

  /**
   * @return configurable for customizer-specific options
   */
  public @Nullable UnnamedConfigurable getConfigurable(@NotNull Project project) {
    return null;
  }

  /**
   * @return settings that will be shown together with other New Terminal settings.
   */
  @ApiStatus.Experimental
  public @Nullable UnnamedConfigurable getBlockTerminalConfigurable(@NotNull Project project) {
    return null;
  }

  /**
   * @return path to the directory to run the terminal in or null if default directory should be used
   */
  protected @Nullable String getDefaultFolder(@NotNull Project project) {
    return null;
  }
}
