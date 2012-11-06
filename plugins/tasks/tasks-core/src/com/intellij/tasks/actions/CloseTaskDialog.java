/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.intellij.tasks.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.tasks.LocalTask;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class CloseTaskDialog extends DialogWrapper {

  private JCheckBox myCommitChanges;
  private JCheckBox myCloseIssue;
  private JPanel myPanel;
  private JLabel myTaskLabel;

  public CloseTaskDialog(Project project, LocalTask task) {
    super(project, false);

    myCommitChanges.setEnabled(!task.isClosedLocally());
    myCloseIssue.setEnabled(task.isIssue());

    myTaskLabel.setIcon(task.getIcon());
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }
}
