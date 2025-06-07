// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;

import javax.swing.*;
import java.awt.*;

public class SvnEditCommitMessageDialog extends DialogWrapper {
  private final @Nullable Project myProject;
  private final String myOldText;
  private CommitMessage myCommitMessage;

  protected SvnEditCommitMessageDialog(@Nullable Project project, final long number, final String oldText) {
    super(project, false);
    myProject = project;
    myOldText = oldText;
    setTitle(SvnBundle.message("svn.edit.commit.message.title", number));
    init();
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    JPanel parentPanel = new JPanel(new BorderLayout());
    final JPanel labelPanel = new JPanel(new GridBagLayout());
    final JLabel label1 = new JLabel(SvnBundle.message("svn.edit.commit.message.attention"));
    parentPanel.setMinimumSize(new Dimension(label1.getPreferredSize().width + 50, 150));
    final GridBagConstraints gb = new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                         JBUI.insets(1), 0, 0);
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
    myCommitMessage = new CommitMessage(myProject, false, true, true);
    Disposer.register(getDisposable(), myCommitMessage);
    myCommitMessage.setText(myOldText);
    parentPanel.add(myCommitMessage, BorderLayout.CENTER);
    return parentPanel;
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return myCommitMessage.getEditorField();
  }

  public @NotNull String getMessage() {
    return myCommitMessage.getComment();
  }

  @Override
  protected @Nullable String getDimensionServiceKey() {
    return "org.jetbrains.idea.svn.history.SvnEditCommitMessageDialog";
  }
}
