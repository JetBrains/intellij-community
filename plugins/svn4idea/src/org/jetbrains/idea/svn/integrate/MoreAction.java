// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;

/**
 * @author irengrig
 */
public abstract class MoreAction extends DumbAwareAction implements CustomComponentAction {
  public static final String LOAD_MORE = "Load more";
  protected final JLabel myLabel;
  private final JPanel myPanel;
  private boolean myEnabled;
  private boolean myVisible;
  private final JButton myLoadMoreBtn;

  protected MoreAction() {
    this(LOAD_MORE);
  }

  protected MoreAction(final String name) {
    myPanel = new JPanel();
    final BoxLayout layout = new BoxLayout(myPanel, BoxLayout.X_AXIS);
    myPanel.setLayout(layout);
    myLoadMoreBtn = new JButton(name);
    myLoadMoreBtn.setMargin(JBUI.insets(2));
    myLoadMoreBtn.addActionListener(e -> this.actionPerformed(null));
    myPanel.add(myLoadMoreBtn);
    myLabel = new JLabel("Loading...");
    myLabel.setForeground(UIUtil.getInactiveTextColor());
    myLabel.setBorder(JBUI.Borders.empty(1, 3, 1, 1));
    myPanel.add(myLabel);
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    return myPanel;
  }

  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
    myLoadMoreBtn.setVisible(myEnabled);
    myLabel.setVisible(! myEnabled);
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(myEnabled);
    e.getPresentation().setVisible(myVisible);
  }

  public void setVisible(boolean b) {
    myVisible = b;
  }
}
