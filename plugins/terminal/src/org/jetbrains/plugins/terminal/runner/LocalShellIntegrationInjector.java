// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.runner;

import com.intellij.execution.CommandLineUtil;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.platform.eel.EelDescriptor;
import com.intellij.platform.eel.provider.EelProviderUtil;
import com.intellij.platform.eel.provider.LocalEelDescriptor;
import com.intellij.platform.eel.provider.utils.EelPathUtils;
import com.intellij.terminal.ui.TerminalWidget;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import kotlin.jvm.functions.Function0;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.plugins.terminal.*;
import org.jetbrains.plugins.terminal.shell_integration.CommandBlockIntegration;
import org.jetbrains.plugins.terminal.util.ShellIntegration;
import org.jetbrains.plugins.terminal.util.ShellNameUtil;
import org.jetbrains.plugins.terminal.util.ShellType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.intellij.platform.eel.provider.EelNioBridgeServiceKt.asEelPath;
import static com.intellij.platform.eel.provider.utils.EelPathUtils.transferLocalContentToRemote;
import static org.jetbrains.plugins.terminal.LocalTerminalDirectRunner.LOGIN_CLI_OPTIONS;
import static org.jetbrains.plugins.terminal.LocalTerminalDirectRunner.isBlockTerminalSupported;

@ApiStatus.Internal
public final class LocalShellIntegrationInjector {
  @VisibleForTesting
  public static final String IJ_ZSH_DIR = "JETBRAINS_INTELLIJ_ZSH_DIR";
  private static final Logger LOG = Logger.getInstance(LocalShellIntegrationInjector.class);
  private static final String LOGIN_SHELL = "LOGIN_SHELL";
  private static final String IJ_COMMAND_END_MARKER = "JETBRAINS_INTELLIJ_COMMAND_END_MARKER";
  private static final String JEDITERM_USER_RCFILE = "JEDITERM_USER_RCFILE";
  private static final String ZDOTDIR = "ZDOTDIR";
  private static final String IJ_COMMAND_HISTORY_FILE_ENV = "__INTELLIJ_COMMAND_HISTFILE__";
  private static final String BASH_RCFILE_OPTION = "--rcfile";
  private static final String SHELL_INTEGRATIONS_DIR_NAME = "shell-integrations";

  // todo: it would be great to extract block terminal configuration from here
  public static @NotNull ShellStartupOptions injectShellIntegration(@NotNull ShellStartupOptions options,
                                                                    boolean isGenOneTerminal,
                                                                    boolean isGenTwoTerminal) {
    List<String> shellCommand = options.getShellCommand();
    String shellExe = ContainerUtil.getFirstItem(shellCommand);
    if (shellCommand == null || shellExe == null) return options;

    List<String> arguments = new ArrayList<>(shellCommand.subList(1, shellCommand.size()));
    Map<String, String> envs = ShellStartupOptionsKt.createEnvVariablesMap(options.getEnvVariables());
    ShellIntegration integration = null;

    List<String> resultCommand = new ArrayList<>();
    resultCommand.add(shellExe);

    String shellName = PathUtil.getFileName(shellExe);
    Path rcFile = findRCFile(shellName);
    String remoteRcFilePath = rcFile != null ? transferAndGetRemotePath(rcFile, options.getWorkingDirectory()) : null;
    if (remoteRcFilePath != null) {
      boolean isBlockTerminal = isBlockTerminalSupported(shellName);
      if (ShellNameUtil.isBash(shellName) || (SystemInfo.isMac && shellName.equals(ShellNameUtil.SH_NAME))) {
        addBashRcFileArgument(envs, arguments, resultCommand, remoteRcFilePath);
        // remove --login to enable --rcfile sourcing
        boolean loginShell = arguments.removeAll(LOGIN_CLI_OPTIONS);
        setLoginShellEnv(envs, loginShell);
        setCommandHistoryFile(options, envs);
        integration = new ShellIntegration(ShellType.BASH, isBlockTerminal ? new CommandBlockIntegration() : null);
      }
      else if (ShellNameUtil.isZshName(shellName)) {
        String originalZDotDir = envs.get(ZDOTDIR);
        if (StringUtil.isNotEmpty(originalZDotDir)) {
          envs.put("JETBRAINS_INTELLIJ_ORIGINAL_ZDOTDIR", originalZDotDir);
        }
        String intellijZDotDir = PathUtil.getParentPath(remoteRcFilePath);
        envs.put(ZDOTDIR, intellijZDotDir);
        envs.put(IJ_ZSH_DIR, PathUtil.getParentPath(intellijZDotDir));
        integration = new ShellIntegration(ShellType.ZSH, isBlockTerminal ? new CommandBlockIntegration() : null);
      }
      else if (shellName.equals(ShellNameUtil.FISH_NAME)) {
        // `--init-command=COMMANDS` is available since Fish 2.7.0 (released November 23, 2017)
        // Multiple `--init-command=COMMANDS` are supported.
        resultCommand.add("--init-command=source " + CommandLineUtil.posixQuote(remoteRcFilePath));
        integration = new ShellIntegration(ShellType.FISH, isBlockTerminal ? new CommandBlockIntegration() : null);
      }
      else if (ShellNameUtil.isPowerShell(shellName)) {
        resultCommand.addAll(arguments);
        arguments.clear();
        resultCommand.addAll(List.of("-NoExit", "-ExecutionPolicy", "Bypass", "-File", remoteRcFilePath));
        integration = new ShellIntegration(ShellType.POWERSHELL, isBlockTerminal ? new CommandBlockIntegration(true) : null);
      }
    }

    if ((isGenOneTerminal || isGenTwoTerminal) && integration != null && integration.getCommandBlockIntegration() != null) {
      // If Gen1 is enabled, use its integration even if Gen2 is enabled.
      // So the Gen1 setting takes precedence over Gen2 setting.
      var commandBlocksOption = isGenOneTerminal
                                ? "INTELLIJ_TERMINAL_COMMAND_BLOCKS"
                                : "INTELLIJ_TERMINAL_COMMAND_BLOCKS_REWORKED";
      envs.put(commandBlocksOption, "1");
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

  private static @Nullable Path findRCFile(@NotNull String shellName) {
    String rcfile = switch (shellName) {
      case ShellNameUtil.BASH_NAME, ShellNameUtil.SH_NAME -> "shell-integrations/bash/bash-integration.bash";
      case ShellNameUtil.ZSH_NAME -> "shell-integrations/zsh/zdotdir/.zshenv";
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
  public static @NotNull Path findAbsolutePath(@NotNull String relativePath) throws IOException {
    String jarPath = PathUtil.getJarPathForClass(LocalTerminalDirectRunner.class);
    final Path result;
    if (PluginManagerCore.isRunningFromSources()) {
      result = Path.of(PathManager.getCommunityHomePath()).resolve("plugins/terminal/resources/").resolve(relativePath);
    } else if (jarPath.endsWith(".jar")) {
      Path jarFile = Path.of(jarPath);
      if (!Files.isRegularFile(jarFile)) {
        throw new IOException("Broken installation: " + jarPath + " is not a file");
      }
      // Find "plugins/terminal" by "plugins/terminal/lib/terminal.jar"
      Path pluginBaseDir = jarFile.getParent().getParent();
      result = pluginBaseDir.resolve(relativePath);
    }
    else {
      Application application = ApplicationManager.getApplication();
      if (application != null && application.isInternal()) {
        jarPath = StringUtil.trimEnd(jarPath.replace('\\', '/'), '/') + '/';
        String srcDir = jarPath.replace("/out/classes/production/intellij.terminal/",
                                        "/community/plugins/terminal/resources/");
        if (Files.isDirectory(Path.of(srcDir))) {
          jarPath = srcDir;
        }
      }
      result = Path.of(jarPath).resolve(relativePath);
    }
    if (!Files.isRegularFile(result)) {
      throw new IOException("Cannot find " + relativePath + ": " + result + " is not a file");
    }
    return result;
  }

  private static void setLoginShellEnv(@NotNull Map<String, String> envs, boolean loginShell) {
    if (loginShell) {
      envs.put(LOGIN_SHELL, "1");
    }
  }

  /**
   * Transfers the specified local file or directory to the remote environment (if necessary)
   * and returns the corresponding remote path.
   * Since the file depends on other shell integration files, all related shell integration files
   * are transferred together, preserving their relative paths.
   *
   * @param localFileOrDir the path to the local file or directory to be transferred
   * @param workingDir the working directory pointing to the remote environment
   * @return the remote path corresponding to the transferred file or directory if the transfer was successful,
   *         or the original path if no transfer was needed; null if an error occurs.
   */
  private static @Nullable String transferAndGetRemotePath(@NotNull Path localFileOrDir, @Nullable String workingDir) {
    EelDescriptor eelDescriptor = findEelDescriptor(workingDir);
    if (eelDescriptor == LocalEelDescriptor.INSTANCE) return localFileOrDir.toString();
    if (eelDescriptor == null) return localFileOrDir.toString();
    Path baseDirectory = findUpShellIntegrationBaseDirectory(localFileOrDir);
    if (baseDirectory == null) return null;
    try {
      String relativePath = baseDirectory.relativize(localFileOrDir).toString();
      Instant started = Instant.now();
      Path remoteBaseDirectory = transferLocalContentToRemote(baseDirectory, new EelPathUtils.TransferTarget.Temporary(eelDescriptor));
      if (LOG.isDebugEnabled()) {
        LOG.debug("Transferred shell integration files to remote (" + eelDescriptor.getMachine().getName() + ") in "
                  + Duration.between(started, Instant.now()).toMillis() + "ms: "
                  + baseDirectory + " -> " + remoteBaseDirectory
         );
      }
      return asEelPath(remoteBaseDirectory.resolve(relativePath)).toString();
    }
    catch (Exception e) {
      LOG.info("Unable to transfer shell integration (" + baseDirectory + ") to remote (" + eelDescriptor + ")", e);
      return null;
    }
  }

  private static @Nullable EelDescriptor findEelDescriptor(@Nullable String workingDir) {
    if (!TerminalStartupKt.shouldUseEelApi()) return null;
    if (Strings.isEmptyOrSpaces(workingDir)) {
      LOG.warn("Empty working directory: " + workingDir);
      return null;
    }
    Path workingDirectoryNioPath;
    try {
      workingDirectoryNioPath = Path.of(workingDir);
    }
    catch (InvalidPathException e) {
      LOG.warn("Invalid working directory: " + workingDir, e);
      return null;
    }
    return EelProviderUtil.getEelDescriptor(workingDirectoryNioPath);
  }

  /**
   * Returns the highest-level directory containing all shell integration files related to the given <code>shellIntegrationFile</code>.
   * For example, given "/path/to/shell-integrations/zsh/zdotdir/.zshenv", it will return "/path/to/shell-integrations/zsh/".
   */
  private static @Nullable Path findUpShellIntegrationBaseDirectory(@NotNull Path shellIntegrationFile) {
    Path f = shellIntegrationFile;
    Path parent = f.getParent();
    while (parent != null && !NioFiles.getFileName(parent).equals(SHELL_INTEGRATIONS_DIR_NAME)) {
      f = parent;
      parent = parent.getParent();
    }
    if (parent == null) {
      LOG.warn("Unable to find shell integration directory for " + shellIntegrationFile);
      return null;
    }
    return f;
  }

  private static void addBashRcFileArgument(Map<String, String> envs,
                                            List<String> arguments,
                                            List<String> result,
                                            @NotNull String rcFilePath) {

    result.add(BASH_RCFILE_OPTION);
    result.add(rcFilePath);
    int idx = arguments.indexOf(BASH_RCFILE_OPTION);
    if (idx >= 0) {
      arguments.remove(idx);
      if (idx < arguments.size()) {
        String userRcFile = FileUtil.expandUserHome(arguments.get(idx));
        // do not set the same RC file path to avoid sourcing recursion
        if (!userRcFile.equals(rcFilePath)) {
          envs.put(JEDITERM_USER_RCFILE, userRcFile);
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
