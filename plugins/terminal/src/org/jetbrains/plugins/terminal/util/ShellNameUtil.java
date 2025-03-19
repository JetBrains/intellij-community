// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.util;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ShellNameUtil {
  public static final String BASH_NAME = "bash";
  public static final String SH_NAME = "sh";
  public static final String ZSH_NAME = "zsh";
  public static final String FISH_NAME = "fish";

  public static boolean isPowerShell(@NotNull String shellName) {
    return shellName.equalsIgnoreCase("powershell") ||
           shellName.equalsIgnoreCase("powershell.exe") ||
           shellName.equalsIgnoreCase("pwsh") ||
           shellName.equalsIgnoreCase("pwsh.exe");
  }

  public static boolean isBash(String shellName) {
    return shellName.equals(BASH_NAME);
  }

  public static boolean isZshName(String shellName) {
    return shellName.equals(ZSH_NAME);
  }

  public static boolean isBashZshFish(@NotNull String shellName) {
    return shellName.equals(BASH_NAME) || (SystemInfo.isMac && shellName.equals(
      SH_NAME)) ||
           shellName.equals(ZSH_NAME) || shellName.equals(FISH_NAME);
  }
}
