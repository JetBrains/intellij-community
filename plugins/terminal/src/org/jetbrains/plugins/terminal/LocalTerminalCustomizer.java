// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.platform.eel.EelDescriptor;
import com.intellij.platform.eel.provider.LocalEelDescriptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.runner.TerminalCustomizerLocalPathTranslator;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @deprecated use {@link org.jetbrains.plugins.terminal.startup.ShellExecOptionsCustomizer} instead
 */
@Deprecated
public abstract class LocalTerminalCustomizer {
  public static final ExtensionPointName<LocalTerminalCustomizer> EP_NAME =
    ExtensionPointName.create("org.jetbrains.plugins.terminal.localTerminalCustomizer");

  /**
   * May alter the command to be run in terminal and/or adjust starting environment. <p/>
   * Please note that environment variables are local to the remote {@code eelDescriptor}.
   * For example, it should contain {@code /path/to/dir} instead of
   * {@code \\wsl.localhost\Ubuntu\path\to\dir} in the case of WSL. <p/>
   * Use the following approach to translate local paths to remote ones:
   * <pre>{@code
   *   private String translateLocalPathToRemote(
   *     String localPathString,
   *     EelDescriptor eelDescriptor
   *   ) throws InvalidPathException, EelPathException {
   *     Path nioPath = Path.of(localPathString);
   *     return EelNioBridgeServiceKt.asEelPath(nioPath, eelDescriptor).toString();
   *   }
   * }</pre>
   * One of the common use cases is modifying the PATH environment variable.
   * The PATH environment variable should also contain path entries local to the remote {@code eelDescriptor}
   * and the path entries should be joined with the remote path separator:
   * {@code EelPlatformKt.getPathSeparator(eelDescriptor.getOsFamily())}.
   *
   * @param project          current project
   * @param workingDirectory working directory
   * @param shellCommand     original command to run
   * @param envs             mutable map of environment variables
   * @param eelDescriptor    descriptor of the environment (in the local case, it's {@link LocalEelDescriptor})
   * @return new command to run. Original {@code command} should be returned if no alterations performed
   * @apiNote terminal starting shell session with the user-specified shell.
   * Note, if the shell integration is enabled, the passed in parameters ({@code shellCommand}, {@code envs}) may be altered.
   * For example, Bash can be run with custom rcfile, e.g.:
   * {@code /usr/bin/bash --rcfile PATH_TO/bash-integration.bash}.
   * See the {@code bash-integration.bash} script
   * for more information on how to alter the execution process.
   */
  public @NotNull List<String> customizeCommandAndEnvironment(
    @NotNull Project project,
    @Nullable String workingDirectory,
    @NotNull List<String> shellCommand,
    @NotNull Map<String, String> envs,
    @NotNull EelDescriptor eelDescriptor
  ) {
    var pathTranslator = new TerminalCustomizerLocalPathTranslator(eelDescriptor, envs, getClass());
    var result = customizeCommandAndEnvironment(project, workingDirectory, shellCommand.toArray(String[]::new), envs);
    pathTranslator.translate();
    return Arrays.asList(result);
  }

  /**
   * @deprecated use {@link #customizeCommandAndEnvironment(Project, String, List, Map, EelDescriptor)}
   */
  @Deprecated
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
   * @deprecated use {@link org.jetbrains.plugins.terminal.settings.TerminalSettingsProvider} instead
   * @return configurable for customizer-specific options
   */
  @Deprecated
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
