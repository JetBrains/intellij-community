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
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.ui.CommitMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;

import javax.swing.*;
import java.awt.*;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 11/16/12
 * Time: 2:55 PM
 */
public class SvnEditCommitMessageDialog extends DialogWrapper {
  @Nullable private final Project myProject;
  private final String myOldText;
  private CommitMessage myCommitMessage;

  protected SvnEditCommitMessageDialog(@Nullable Project project, final long number, final String oldText) {
    super(project, false);
    myProject = project;
    myOldText = oldText;
    setTitle(SvnBundle.message("svn.edit.commit.message.title", number));
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    JPanel parentPanel = new JPanel(new BorderLayout());
    final JPanel labelPanel = new JPanel(new GridBagLayout());
    final JLabel label1 = new JLabel(SvnBundle.message("svn.edit.commit.message.attention"));
    parentPanel.setMinimumSize(new Dimension(label1.getPreferredSize().width + 50, 150));
    final GridBagConstraints gb = new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                                  new Insets(1, 1, 1, 1), 0, 0);
    label1.setFont(label1.getFont().deriveFont(Font.BOLD));
    labelPanel.add(label1, gb);
    ++ gb.gridy;
    gb.insets.top = 5;
    gb.insets.bottom = 3;
    final JLabel label2 = new JLabel(SvnBundle.message("svn.edit.commit.message.prompt"));
    labelPanel.add(label2, gb);
    final JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.add(labelPanel, BorderLayout.WEST);
    parentPanel.add(wrapper, BorderLayout.NORTH);
    myCommitMessage = new CommitMessage(myProject, false);
    myCommitMessage.setCheckSpelling(true);
    myCommitMessage.setText(myOldText);
    parentPanel.add(myCommitMessage, BorderLayout.CENTER);
    return parentPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myCommitMessage.getEditorField();
  }

  @NotNull
  public String getMessage() {
    return myCommitMessage.getComment();
  }

  @Nullable
  @Override
  protected String getDimensionServiceKey() {
    return "org.jetbrains.idea.svn.history.SvnEditCommitMessageDialog";
  }
}
