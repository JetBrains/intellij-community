// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.runner;

import com.intellij.openapi.util.io.NioFiles;
import com.intellij.platform.eel.EelDescriptor;
import com.intellij.platform.eel.EelPlatformKt;
import com.intellij.platform.eel.provider.LocalEelDescriptor;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner;
import org.jetbrains.plugins.terminal.TerminalStartupKt;
import org.jetbrains.plugins.terminal.util.ShellNameUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class LocalTerminalStartCommandBuilder {
  public static final String INTERACTIVE_CLI_OPTION = "-i";

  public static @NotNull List<String> convertShellPathToCommand(@NotNull String shellPath) {
    return convertShellPathToCommand(shellPath, shellCommand -> LocalEelDescriptor.INSTANCE);
  }

  public static @NotNull List<String> convertShellPathToCommand(@NotNull String shellPath, @NotNull String workingDirectory) {
    return convertShellPathToCommand(shellPath, shellCommand -> {
      return TerminalStartupKt.findEelDescriptor(workingDirectory, shellCommand);
    });
  }

  private static @NotNull List<String> convertShellPathToCommand(
    @NotNull String shellPath,
    @NotNull Function<List<String>, EelDescriptor> eelDescriptorProvider
  ) {
    List<String> shellCommand;
    if (isAbsoluteFilePathAndExists(shellPath)) {
      shellCommand = List.of(shellPath);
    }
    else {
      shellCommand = ParametersListUtil.parse(shellPath, false, OS.CURRENT != OS.Windows);
    }
    EelDescriptor eelDescriptor = eelDescriptorProvider.apply(shellCommand);
    String shellExe = ContainerUtil.getFirstItem(shellCommand);
    if (shellExe == null) return shellCommand;
    String shellName = PathUtil.getFileName(shellExe);
    if (!containsLoginOrInteractiveOption(shellCommand)) {
      shellCommand = new ArrayList<>(shellCommand);
      if (isLoginOptionAvailable(shellName) && isLoginShellNeeded(eelDescriptor)) {
        shellCommand.add(LocalTerminalDirectRunner.LOGIN_CLI_OPTION);
      }
      if (isInteractiveOptionAvailable(shellName)) {
        shellCommand.add(INTERACTIVE_CLI_OPTION);
      }
    }
    return List.copyOf(shellCommand);
  }

  private static boolean isLoginShellNeeded(@NotNull EelDescriptor eelDescriptor) {
    return eelDescriptor == LocalEelDescriptor.INSTANCE && OS.CURRENT == OS.macOS ||
           eelDescriptor != LocalEelDescriptor.INSTANCE && EelPlatformKt.isPosix(eelDescriptor.getOsFamily());
  }

  private static boolean isAbsoluteFilePathAndExists(@NotNull String path) {
    Path file = NioFiles.toPath(path);
    return file != null && file.isAbsolute() && Files.isRegularFile(file);
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
