// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.actions;

import com.intellij.openapi.actionSystem.ShortcutProvider;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.impl.TaskUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
abstract class TaskListItem implements ShortcutProvider {

  private final @NlsContexts.ListItem String myText;
  private final Icon myIcon;
  private final @NlsContexts.Separator String mySeparator;
  private final boolean myTemp;
  private final LocalTask myTask;

  TaskListItem(@NlsContexts.ListItem String text, Icon icon) {
    myText = text;
    myIcon = icon;
    mySeparator = null;
    myTask = null;
    myTemp = false;
  }

  protected TaskListItem(LocalTask task, @NlsContexts.Separator String separator, boolean temp) {
    myTask = task;
    mySeparator = separator;
    myTemp = temp;
    myText = TaskUtil.getTrimmedSummary(task);
    myIcon = temp ? IconLoader.getTransparentIcon(task.getIcon(), 0.5f) : task.getIcon();
  }

  public @NlsContexts.ListItem String getText() {
    return myText;
  }

  public Icon getIcon() {
    return myIcon;
  }

  public @Nullable @NlsContexts.Separator String getSeparator() {
    return mySeparator;
  }

  abstract void select();

  public @Nullable LocalTask getTask() {
    return myTask;
  }

  public boolean isTemp() {
    return myTemp;
  }

  @Override
  public @Nullable ShortcutSet getShortcut() {
    return null;
  }
}
