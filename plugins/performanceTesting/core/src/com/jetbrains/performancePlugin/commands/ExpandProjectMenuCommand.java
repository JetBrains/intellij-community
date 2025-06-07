// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.IdeActions;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Command to test how long does it take to open context menu in project view.
 * The popup won't be shown but all heavy methods that may cause context menu long load will be invoked.
 * Example: %expandProjectMenu
 */
public class ExpandProjectMenuCommand extends ExpandMenuCommand {
  public static final @NonNls String PREFIX = CMD_PREFIX + "expandProjectMenu";

  public ExpandProjectMenuCommand(@NotNull String text, int line) {
    super(text, line);
  }

  @Override
  protected String getSpanName() {
    return PREFIX;
  }

  @Override
  protected @NotNull String getGroupId() {
    return IdeActions.GROUP_PROJECT_VIEW_POPUP;
  }

  @Override
  protected @NotNull String getPlace() {
    return ActionPlaces.PROJECT_VIEW_POPUP;
  }
}
