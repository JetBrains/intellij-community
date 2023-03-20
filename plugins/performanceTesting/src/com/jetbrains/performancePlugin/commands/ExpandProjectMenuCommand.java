package com.jetbrains.performancePlugin.commands;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.IdeActions;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

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
