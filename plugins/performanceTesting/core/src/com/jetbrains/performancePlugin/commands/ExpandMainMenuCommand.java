// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.IdeActions;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class ExpandMainMenuCommand extends ExpandMenuCommand {

  public static final @NonNls String PREFIX = CMD_PREFIX + "expandMainMenu";

  public ExpandMainMenuCommand(@NotNull String text, int line) {
    super(text, line);
  }


  @Override
  protected String getSpanName() {
    return PREFIX;
  }

  @Override
  protected @NotNull String getGroupId() {
    return IdeActions.GROUP_MAIN_MENU;
  }

  @Override
  protected @NotNull String getPlace() {
    return ActionPlaces.MAIN_MENU;
  }
}