// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal;

import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public final class TerminalIcons {
  private static @NotNull Icon load(@NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, TerminalIcons.class.getClassLoader(), cacheKey, flags);
  }
  private static @NotNull Icon load(@NotNull String expUIPath, @NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, expUIPath, TerminalIcons.class.getClassLoader(), cacheKey, flags);
  }
  /** 16x16 */ public static final @NotNull Icon Command = load("icons/command.svg", 957086477, 2);
  /** 13x13 */ public static final @NotNull Icon OpenTerminal_13x13 = load("icons/expui/toolwindow/terminal.svg", "icons/OpenTerminal_13x13.svg", 1939257758, 2);
  /** 16x16 */ public static final @NotNull Icon Option = load("icons/option.svg", -1363443188, 2);
  /** 16x16 */ public static final @NotNull Icon Other = load("icons/other.svg", 2034971647, 2);
  /** 16x16 */ public static final @NotNull Icon OtherFile = load("icons/otherFile.svg", 1021254284, 2);
  /** 16x16 */ public static final @NotNull Icon SearchInBlock = load("icons/searchInBlock.svg", 929032536, 2);
}
