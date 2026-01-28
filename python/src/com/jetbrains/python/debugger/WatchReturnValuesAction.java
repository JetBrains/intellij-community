// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.xdebugger.XDebugProcess;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.debugger.settings.PyDebuggerSettings;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

@ApiStatus.Internal
public final class WatchReturnValuesAction extends ToggleAction {
  private volatile boolean myWatchesReturnValues;
  private final XDebugProcess myProcess;
  private final @NlsActions.ActionText String myText;
  private final Consumer<Boolean> myToggleCallback;

  public WatchReturnValuesAction(@NotNull XDebugProcess frameAccessor, Consumer<Boolean> toggleCallback) {
    super("", PyBundle.message("debugger.watch.return.values.description"), null);
    myWatchesReturnValues = PyDebuggerSettings.getInstance().isWatchReturnValues();
    myProcess = frameAccessor;
    myText = PyBundle.message("debugger.watch.show.return.values");
    myToggleCallback = toggleCallback;
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    super.update(e);
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(true);
    presentation.setText(myText);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return myWatchesReturnValues;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean watch) {
    myWatchesReturnValues = watch;
    PyDebuggerSettings.getInstance().setWatchReturnValues(watch);
    final Project project = e.getProject();
    if (project != null) {
      if (myToggleCallback != null) {
        myToggleCallback.accept(myWatchesReturnValues);
      }
      myProcess.getSession().rebuildViews();
    }
  }
}