/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.newProject;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.wm.impl.welcomeScreen.CardActionsPanel;
import com.jetbrains.python.newProject.actions.PyCharmNewProjectStep;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class PyCharmNewProjectDialog extends DialogWrapper {
  public PyCharmNewProjectDialog() {
    super(ProjectManager.getInstance().getDefaultProject());
    setTitle(" "); // hack to make native fileChooser work on Mac. See MacFileChooserDialogImpl.MAIN_THREAD_RUNNABLE
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    final Runnable runnable = new Runnable() {
      @Override
      public void run() {
        PyCharmNewProjectDialog.this.close(OK_EXIT_CODE);
      }
    };
    final DefaultActionGroup root = new PyCharmNewProjectStep(runnable);

    return new CardActionsPanel(root) {

      @Override
      public Dimension getPreferredSize() {
        return getMinimumSize();
      }

      @Override
      public Dimension getMinimumSize() {
        return new Dimension(650, 450);
      }
    };
  }

  @Override
  protected String getHelpId() {
    return null;
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[0];
  }
}
