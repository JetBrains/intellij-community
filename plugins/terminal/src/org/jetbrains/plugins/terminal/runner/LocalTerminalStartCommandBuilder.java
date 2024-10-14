// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.runner;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner;
import org.jetbrains.plugins.terminal.util.ShellNameUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public final class LocalTerminalStartCommandBuilder {
  public static final String INTERACTIVE_CLI_OPTION = "-i";

  public static @NotNull List<String> convertShellPathToCommand(@NotNull String shellPath) {
    List<String> shellCommand;
    if (isAbsoluteFilePathAndExists(shellPath)) {
      shellCommand = List.of(shellPath);
    }
    else {
      shellCommand = ParametersListUtil.parse(shellPath, false, !SystemInfo.isWindows);
    }
    String shellExe = ContainerUtil.getFirstItem(shellCommand);
    if (shellExe == null) return shellCommand;
    String shellName = PathUtil.getFileName(shellExe);
    if (!containsLoginOrInteractiveOption(shellCommand)) {
      shellCommand = new ArrayList<>(shellCommand);
      if (isLoginOptionAvailable(shellName) && SystemInfo.isMac) {
        shellCommand.add(LocalTerminalDirectRunner.LOGIN_CLI_OPTION);
      }
      if (isInteractiveOptionAvailable(shellName)) {
        shellCommand.add(INTERACTIVE_CLI_OPTION);
      }
    }
    return List.copyOf(shellCommand);
  }

  private static boolean isAbsoluteFilePathAndExists(@NotNull String path) {
    File file = new File(path);
    return file.isAbsolute() && file.isFile();
  }

  private static boolean containsLoginOrInteractiveOption(List<String> command) {
    return isLogin(command) || command.contains(INTERACTIVE_CLI_OPTION);
  }

  private static boolean isLogin(@NotNull List<String> command) {
    return ContainerUtil.exists(command, LocalTerminalDirectRunner.LOGIN_CLI_OPTIONS::contains);
  }

  private static boolean isLoginOptionAvailable(@NotNull String shellName) {
    return ShellNameUtil.isBashZshFish(shellName);
  }

  private static boolean isInteractiveOptionAvailable(@NotNull String shellName) {
    return ShellNameUtil.isBashZshFish(shellName);
  }
}
