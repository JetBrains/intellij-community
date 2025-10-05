// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver;

import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public final class McpserverIcons {
  private static @NotNull Icon load(@NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, McpserverIcons.class.getClassLoader(), cacheKey, flags);
  }

  public static final class Expui {
    /** 16x16 */ public static final @NotNull Icon StatusDisabled = load("icons/expui/statusDisabled.svg", -456172083, 2);
    /** 16x16 */ public static final @NotNull Icon StatusEnabled = load("icons/expui/statusEnabled.svg", 1099037481, 2);
  }

  /** 16x16 */ public static final @NotNull Icon StatusDisabled = load("icons/statusDisabled.svg", -1854758088, 2);
  /** 16x16 */ public static final @NotNull Icon StatusEnabled = load("icons/statusEnabled.svg", 1900690067, 0);
}
