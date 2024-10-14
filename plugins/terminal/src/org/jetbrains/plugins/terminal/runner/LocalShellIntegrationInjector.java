// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.runner;

import com.intellij.execution.CommandLineUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.terminal.ui.TerminalWidget;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import kotlin.jvm.functions.Function0;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner;
import org.jetbrains.plugins.terminal.ShellStartupOptions;
import org.jetbrains.plugins.terminal.ShellStartupOptionsKt;
import org.jetbrains.plugins.terminal.ShellTerminalWidget;
import org.jetbrains.plugins.terminal.shell_integration.CommandBlockIntegration;
import org.jetbrains.plugins.terminal.util.ShellIntegration;
import org.jetbrains.plugins.terminal.util.ShellNameUtil;
import org.jetbrains.plugins.terminal.util.ShellType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.jetbrains.plugins.terminal.LocalTerminalDirectRunner.LOGIN_CLI_OPTIONS;
import static org.jetbrains.plugins.terminal.LocalTerminalDirectRunner.isBlockTerminalSupported;

@ApiStatus.Internal
public class LocalShellIntegrationInjector {

  public static final String IJ_ZSH_DIR = "JETBRAINS_INTELLIJ_ZSH_DIR";
  private static final Logger LOG = Logger.getInstance(LocalShellIntegrationInjector.class);
  private static final String LOGIN_SHELL = "LOGIN_SHELL";
  private static final String IJ_COMMAND_END_MARKER = "JETBRAINS_INTELLIJ_COMMAND_END_MARKER";
  private static final String JEDITERM_USER_RCFILE = "JEDITERM_USER_RCFILE";
  private static final String ZDOTDIR = "ZDOTDIR";
  private static final String IJ_COMMAND_HISTORY_FILE_ENV = "__INTELLIJ_COMMAND_HISTFILE__";
  private final Supplier<Boolean> myBlockTerminalEnabled;

  public LocalShellIntegrationInjector(Supplier<Boolean> isBlockTerminalEnabled) {
    myBlockTerminalEnabled = isBlockTerminalEnabled;
  }

  public @NotNull ShellStartupOptions configureStartupOptions(@NotNull ShellStartupOptions baseOptions) {
    return injectShellIntegration(baseOptions, myBlockTerminalEnabled.get());
  }

  @Nullable
  private static String findRCFile(@NotNull String shellName) {
    String rcfile = switch (shellName) {
      case ShellNameUtil.BASH_NAME, ShellNameUtil.SH_NAME -> "shell-integrations/bash/bash-integration.bash";
      case ShellNameUtil.ZSH_NAME -> "shell-integrations/zsh/.zshenv";
      case ShellNameUtil.FISH_NAME -> "shell-integrations/fish/fish-integration.fish";
      default -> null;
    };
    if (rcfile == null && ShellNameUtil.isPowerShell(shellName)) {
      rcfile = "shell-integrations/powershell/powershell-integration.ps1";
    }
    if (rcfile != null) {
      try {
        return findAbsolutePath(rcfile);
      }
      catch (Exception e) {
        LOG.warn("Unable to find " + rcfile + " configuration file", e);
      }
    }
    return null;
  }

  @VisibleForTesting
  static @NotNull String findAbsolutePath(@NotNull String relativePath) throws IOException {
    String jarPath = PathUtil.getJarPathForClass(LocalTerminalDirectRunner.class);
    final File result;
    if (jarPath.endsWith(".jar")) {
      File jarFile = new File(jarPath);
      if (!jarFile.isFile()) {
        throw new IOException("Broken installation: " + jarPath + " is not a file");
      }
      File pluginBaseDir = jarFile.getParentFile().getParentFile();
      result = new File(pluginBaseDir, relativePath);
    }
    else {
      Application application = ApplicationManager.getApplication();
      if (application != null && application.isInternal()) {
        jarPath = StringUtil.trimEnd(jarPath.replace('\\', '/'), '/') + '/';
        String srcDir = jarPath.replace("/out/classes/production/intellij.terminal/",
                                        "/community/plugins/terminal/resources/");
        if (new File(srcDir).isDirectory()) {
          jarPath = srcDir;
        }
      }
      result = new File(jarPath, relativePath);
    }
    if (!result.isFile()) {
      throw new IOException("Cannot find " + relativePath + ": " + result.getAbsolutePath() + " is not a file");
    }
    return result.getAbsolutePath();
  }

  // todo: it would be great to extract block terminal configuration from here
  private static @NotNull ShellStartupOptions injectShellIntegration(@NotNull ShellStartupOptions options, boolean blockTerminalEnabled) {
    List<String> shellCommand = options.getShellCommand();
    String shellExe = ContainerUtil.getFirstItem(shellCommand);
    if (shellCommand == null || shellExe == null) return options;

    List<String> arguments = new ArrayList<>(shellCommand.subList(1, shellCommand.size()));
    Map<String, String> envs = ShellStartupOptionsKt.createEnvVariablesMap(options.getEnvVariables());
    ShellIntegration integration = null;

    List<String> resultCommand = new ArrayList<>();
    resultCommand.add(shellExe);

    String shellName = PathUtil.getFileName(shellExe);
    String rcFilePath = findRCFile(shellName);
    if (rcFilePath != null) {
      boolean isBlockTerminal = isBlockTerminalSupported(shellName);
      if (ShellNameUtil.isBash(shellName) || (SystemInfo.isMac && shellName.equals(ShellNameUtil.SH_NAME))) {
        addRcFileArgument(envs, arguments, resultCommand, rcFilePath, "--rcfile");
        // remove --login to enable --rcfile sourcing
        boolean loginShell = arguments.removeAll(LOGIN_CLI_OPTIONS);
        setLoginShellEnv(envs, loginShell);
        setCommandHistoryFile(options, envs);
        integration = new ShellIntegration(ShellType.BASH, isBlockTerminal ? new CommandBlockIntegration() : null);
      }
      else if (ShellNameUtil.isZshName(shellName)) {
        String zdotdir = envs.get(ZDOTDIR);
        if (StringUtil.isNotEmpty(zdotdir)) {
          envs.put("_INTELLIJ_ORIGINAL_ZDOTDIR", zdotdir);
        }
        String zshDir = PathUtil.getParentPath(rcFilePath);
        envs.put(ZDOTDIR, zshDir);
        envs.put(IJ_ZSH_DIR, zshDir);
        integration = new ShellIntegration(ShellType.ZSH, isBlockTerminal ? new CommandBlockIntegration() : null);
      }
      else if (shellName.equals(ShellNameUtil.FISH_NAME)) {
        // `--init-command=COMMANDS` is available since Fish 2.7.0 (released November 23, 2017)
        // Multiple `--init-command=COMMANDS` are supported.
        resultCommand.add("--init-command=source " + CommandLineUtil.posixQuote(rcFilePath));
        integration = new ShellIntegration(ShellType.FISH, isBlockTerminal ? new CommandBlockIntegration() : null);
      }
      else if (ShellNameUtil.isPowerShell(shellName)) {
        resultCommand.addAll(arguments);
        arguments.clear();
        resultCommand.addAll(List.of("-NoExit", "-ExecutionPolicy", "Bypass", "-File", rcFilePath));
        integration = new ShellIntegration(ShellType.POWERSHELL, isBlockTerminal ? new CommandBlockIntegration(true) : null);
      }
    }

    if (blockTerminalEnabled && integration != null && integration.getCommandBlockIntegration() != null) {
      envs.put("INTELLIJ_TERMINAL_COMMAND_BLOCKS", "1");
      // Pretend to be Fig.io terminal to avoid it breaking IntelliJ shell integration:
      // at startup it runs a sub-shell without IntelliJ shell integration
      envs.put("FIG_TERM", "1");
      // CodeWhisperer runs a nested shell unavailable for injecting IntelliJ shell integration.
      // Zsh and Bash are affected although these shell integrations are installed differently.
      // We need to either change how IntelliJ injects shell integrations to support nested shells
      // or disable running a nested shell by CodeWhisperer. Let's do the latter:
      envs.put("PROCESS_LAUNCHED_BY_CW", "1");
      // The same story as the above. Amazon Q is a renamed CodeWhisperer. So, they also renamed the env variables.
      envs.put("PROCESS_LAUNCHED_BY_Q", "1");
    }

    CommandBlockIntegration commandIntegration = integration != null ? integration.getCommandBlockIntegration() : null;
    String commandEndMarker = commandIntegration != null ? commandIntegration.getCommandEndMarker() : null;
    if (commandEndMarker != null) {
      envs.put(IJ_COMMAND_END_MARKER, commandEndMarker);
    }

    resultCommand.addAll(arguments);
    return options.builder()
      .shellCommand(resultCommand)
      .envVariables(envs)
      .shellIntegration(integration)
      .build();
  }

  private static void setLoginShellEnv(@NotNull Map<String, String> envs, boolean loginShell) {
    if (loginShell) {
      envs.put(LOGIN_SHELL, "1");
    }
  }

  private static void addRcFileArgument(Map<String, String> envs,
                                        List<String> arguments,
                                        List<String> result,
                                        String rcFilePath, String rcfileOption) {
    result.add(rcfileOption);
    result.add(rcFilePath);
    int idx = arguments.indexOf(rcfileOption);
    if (idx >= 0) {
      arguments.remove(idx);
      if (idx < arguments.size()) {
        String userRcFile = FileUtil.expandUserHome(arguments.get(idx));
        // do not set the same RC file path to avoid sourcing recursion
        if (!userRcFile.equals(rcFilePath)) {
          envs.put(JEDITERM_USER_RCFILE, FileUtil.expandUserHome(arguments.get(idx)));
        }
        arguments.remove(idx);
      }
    }
  }

  private static void setCommandHistoryFile(@NotNull ShellStartupOptions startupOptions, @NotNull Map<String, String> envs) {
    Function0<Path> commandHistoryFileProvider = startupOptions.getCommandHistoryFileProvider();
    Path commandHistoryFile = commandHistoryFileProvider != null ? commandHistoryFileProvider.invoke() : null;
    if (commandHistoryFile != null) {
      envs.put(IJ_COMMAND_HISTORY_FILE_ENV, commandHistoryFile.toString());
      ShellTerminalWidget widget = getShellTerminalWidget(startupOptions);
      if (widget != null) {
        widget.setCommandHistoryFilePath(commandHistoryFile.toString());
      }
    }
  }

  private static @Nullable ShellTerminalWidget getShellTerminalWidget(@Nullable ShellStartupOptions options) {
    TerminalWidget widget = options != null ? options.getWidget() : null;
    return widget != null ? ShellTerminalWidget.asShellJediTermWidget(widget) : null;
  }

}
