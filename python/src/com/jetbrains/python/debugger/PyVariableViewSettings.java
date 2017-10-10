/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.debugger;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.jetbrains.python.debugger.settings.PyDebuggerSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyVariableViewSettings {
  public static final String PYDEVD_LOAD_VALUES_ASYNC = "PYDEVD_LOAD_VALUES_ASYNC";

  public static class SimplifiedView extends ToggleAction {
    private final PyDebugProcess myProcess;
    private final String myText;
    private volatile boolean mySimplifiedView;

    public SimplifiedView(@Nullable PyDebugProcess debugProcess) {
      super("", "Disables watching classes, functions and modules objects", null);
      mySimplifiedView = PyDebuggerSettings.getInstance().isSimplifiedView();
      myProcess = debugProcess;
      myText = "Simplified Variables View";
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(true);
      presentation.setText(myText);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return mySimplifiedView;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean hide) {
      mySimplifiedView = hide;
      PyDebuggerSettings.getInstance().setSimplifiedView(hide);
      if (myProcess != null) {
        myProcess.getSession().rebuildViews();
      }
    }
  }

  public static class AsyncView extends ToggleAction {
    private final String myText;
    private volatile boolean myLazyVariablesEvaluation;

    public AsyncView() {
      super("", "Load variable values asynchronously", null);
      myLazyVariablesEvaluation = PyDebuggerSettings.getInstance().isLoadValuesAsync();
      myText = "Load Values Asynchronously";
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(true);
      presentation.setText(myText);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myLazyVariablesEvaluation;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean hide) {
      myLazyVariablesEvaluation = hide;
      PyDebuggerSettings.getInstance().setLoadValuesAsync(hide);
    }
  }
}
