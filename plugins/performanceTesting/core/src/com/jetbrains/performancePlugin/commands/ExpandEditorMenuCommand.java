// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.IdeActions;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ExpandEditorMenuCommand extends ExpandMenuCommand {
  public static final @NonNls String PREFIX = CMD_PREFIX + "expandEditorMenu";

  public ExpandEditorMenuCommand(@NotNull String text, int line) {
    super(text, line);
  }

  @Override
  protected String getSpanName() {
    return PREFIX;
  }

  @Override
  protected @NotNull String getGroupId() {
    return IdeActions.GROUP_EDITOR_POPUP;
  }

  @Override
  protected @NotNull String getPlace() {
    return ActionPlaces.EDITOR_POPUP;
  }
}
