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
package com.jetbrains.python.debugger.containerview;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.Function;

/**
 * Created by Yuli Fiterman on 5/10/2016.
 */
public class ViewNumericContainerDialog extends DialogWrapper {
  private final JComponent myMainPanel;

  ViewNumericContainerDialog(@NotNull Project project, Function<ViewNumericContainerDialog, JComponent> tableSupplier) {
    super(project, false);
    setModal(false);
    setCancelButtonText("Close");
    setCrossClosesWindow(true);
    myMainPanel = tableSupplier.apply(this);
    init();
  }

  public void setError(String text) {
    //todo: think about this usage
    setErrorText(text);
  }

  @Override
  @NotNull
  protected Action[] createActions() {
    return new Action[]{getCancelAction()};
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.jetbrains.python.actions.view.array.PyViewNumericContainerAction";
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }
}
