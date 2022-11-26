// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.NamedColorUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author irengrig
 */
public abstract class MoreAction extends DumbAwareAction implements CustomComponentAction {
  protected final JLabel myLabel;
  private final JPanel myPanel;
  private boolean myEnabled;
  private boolean myVisible;
  private final JButton myLoadMoreBtn;

  protected MoreAction(@NlsContexts.Button @NotNull String name) {
    myPanel = new JPanel();
    final BoxLayout layout = new BoxLayout(myPanel, BoxLayout.X_AXIS);
    myPanel.setLayout(layout);
    myLoadMoreBtn = new JButton(name);
    myLoadMoreBtn.setMargin(JBUI.insets(2));
    myLoadMoreBtn.addActionListener(__ -> perform());
    myPanel.add(myLoadMoreBtn);
    myLabel = new JLabel(CommonBundle.getLoadingTreeNodeText());
    myLabel.setForeground(NamedColorUtil.getInactiveTextColor());
    myLabel.setBorder(JBUI.Borders.empty(1, 3, 1, 1));
    myPanel.add(myLabel);
  }

  @NotNull
  @Override
  public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    return myPanel;
  }

  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
    myLoadMoreBtn.setVisible(myEnabled);
    myLabel.setVisible(! myEnabled);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(myEnabled);
    e.getPresentation().setVisible(myVisible);
  }

  public void setVisible(boolean b) {
    myVisible = b;
  }

  public abstract void perform();

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    perform();
  }
}
