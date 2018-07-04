/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.tasks.actions;

import com.intellij.openapi.util.IconLoader;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.impl.TaskUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
abstract class TaskListItem {

  private final String myText;
  private final Icon myIcon;
  private final String mySeparator;
  private final boolean myTemp;
  private final LocalTask myTask;

  public TaskListItem(String text, Icon icon) {
    myText = text;
    myIcon = icon;
    mySeparator = null;
    myTask = null;
    myTemp = false;
  }

  protected TaskListItem(LocalTask task, String separator, boolean temp) {
    myTask = task;
    mySeparator = separator;
    myTemp = temp;
    myText = TaskUtil.getTrimmedSummary(task);
    myIcon = temp ? IconLoader.getTransparentIcon(task.getIcon(), 0.5f) : task.getIcon();
  }

  public String getText() {
    return myText;
  }

  public Icon getIcon() {
    return myIcon;
  }

  @Nullable
  public String getSeparator() {
    return mySeparator;
  }

  abstract void select();

  @Nullable
  public LocalTask getTask() {
    return myTask;
  }

  public boolean isTemp() {
    return myTemp;
  }
}
