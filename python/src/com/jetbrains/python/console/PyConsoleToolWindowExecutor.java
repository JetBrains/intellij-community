/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.console;

import com.intellij.execution.Executor;
import com.intellij.openapi.extensions.Extensions;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Created by Yuli Fiterman on 9/11/2016.
 */
public class PyConsoleToolWindowExecutor extends Executor {


  public static final String ID = "PyConsoleToolWindowExecutor";
  public static final String TOOLWINDOW_ID = "Python Console";

  @Nullable
  public static PyConsoleToolWindowExecutor findInstance() {
    for (Executor t : Extensions.getExtensions(INTERNAL_EXECUTOR_EXTENSION_NAME)) {
      if (PyConsoleToolWindowExecutor.class.isInstance(t)) {
        return (PyConsoleToolWindowExecutor)t;
      }
    }

    return null;
  }

  @Override
  public String getToolWindowId() {
    return TOOLWINDOW_ID;
  }

  @Override
  public Icon getToolWindowIcon() {
    return PythonIcons.Python.PythonConsoleToolWindow;
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return PythonIcons.Python.PythonConsoleToolWindow;
  }

  @Override
  public Icon getDisabledIcon() {
    return null;
  }

  @Override
  public String getDescription() {
    return null;
  }

  @NotNull
  @Override
  public String getActionName() {
    return "Run Python Console";
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @NotNull
  @Override
  public String getStartActionText() {
    return "Starting Python Console";
  }

  @Override
  public String getContextActionId() {
    return "";
  }

  @Override
  public String getHelpId() {
    return null;
  }
}
