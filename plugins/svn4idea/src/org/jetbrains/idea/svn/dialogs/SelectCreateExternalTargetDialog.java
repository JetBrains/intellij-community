// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.HashSet;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Url;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Set;

public class SelectCreateExternalTargetDialog extends RepositoryBrowserDialog {
  private String mySelectedURL;
  private boolean myCheckout;
  private JTextField myFolderName;
  private boolean myFollowRemoteTarget;
  // light check for same file existence - check before
  private final Set<String> myUsedNames;

  public SelectCreateExternalTargetDialog(Project project, final VirtualFile below) {
    super(project, true, "Point to repository location");
    final VirtualFile[] children = below.getChildren();
    myUsedNames = new HashSet<>();
    int maxCnt = 1000;  // maybe not take it too seriously ?
    for (VirtualFile child : children) {
      myUsedNames.add(child.getName());
      -- maxCnt;
      if (maxCnt <= 0) break;
    }
  }

  @Override
  protected void init() {
    super.init();
    myFollowRemoteTarget = true;
    setTitle("Select Target For External");
    setOKButtonText("Select");
    getRepositoryBrowser().addChangeListener(e -> {
      if (getOKAction() != null) {
        final String selectedURL = getRepositoryBrowser().getSelectedURL();
        if (myFollowRemoteTarget && selectedURL != null) {
          myFolderName.setText(Url.tail(selectedURL));
        }
        checkEnabled();
      }
    });
    getOKAction().setEnabled(getRepositoryBrowser().getSelectedURL() != null);
  }

  private void checkEnabled() {
    final String selectedURL = getRepositoryBrowser().getSelectedURL();
    final String text = myFolderName.getText();
    final boolean contains = myUsedNames.contains(text);
    final boolean enabled = selectedURL != null && !StringUtil.isEmptyOrSpaces(text) && !contains;
    if (contains) {
      setErrorText("Target File Already Exists", myFolderName);
    } else {
      setErrorText(null);
    }
    getOKAction().setEnabled(enabled);
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[] {getOKAction(), getCancelAction()};
  }
  @Override
  protected void doOKAction() {
    mySelectedURL = getRepositoryBrowser().getSelectedURL();
    super.doOKAction();
  }

  @Override
  public String getSelectedURL() {
    return mySelectedURL;
  }

  @Override
  protected JPopupMenu createPopup(boolean toolWindow) {
    DefaultActionGroup group = new DefaultActionGroup();
    DefaultActionGroup newGroup = DefaultActionGroup.createPopupGroup(() -> "_New");
    final RepositoryBrowserComponent browser = getRepositoryBrowser();
    newGroup.add(new AddLocationAction(browser));
    newGroup.add(new MkDirAction(browser));
    group.add(newGroup);
    group.addSeparator();
    group.add(new RefreshAction(browser));
    group.add(new DiscardLocationAction(browser));
    ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu("", group);
    return menu.getComponent();
  }

  @Override
  public JComponent createCenterPanel() {
    final JComponent repositoryPanel = super.createCenterPanel();
    final JPanel wrapper = new JPanel(new GridBagLayout());
    final GridBagConstraints gb = new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                                                         JBUI.insets(1), 0, 0);
    gb.weightx = 1;
    gb.weighty = 1;
    gb.gridwidth = 2;
    wrapper.add(repositoryPanel, gb);
    ++ gb.gridy;
    gb.fill = GridBagConstraints.NONE;
    gb.weightx = 0;
    gb.weighty = 0;
    gb.gridwidth = 1;

    myFolderName = new JTextField();
    gb.insets.top = 5;
    gb.anchor = GridBagConstraints.WEST;
    wrapper.add(new JLabel("Local Target:"), gb);
    ++ gb.gridx;
    gb.weightx = 1;
    gb.fill = GridBagConstraints.HORIZONTAL;
    wrapper.add(myFolderName, gb);
    gb.insets.top = 1;
    gb.weightx = 0;
    gb.fill = GridBagConstraints.NONE;
    gb.gridx = 0;
    ++ gb.gridy;

    myFolderName.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        myFollowRemoteTarget = false;
        myFolderName.removeFocusListener(this);
      }
    });
    myFolderName.addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent e) {
      }

      @Override
      public void keyReleased(KeyEvent e) {
        checkEnabled();
      }
    });
    myFolderName.addInputMethodListener(new InputMethodListener() {
      @Override
      public void inputMethodTextChanged(InputMethodEvent event) {
        checkEnabled();
      }
      @Override
      public void caretPositionChanged(InputMethodEvent event) {
      }
    });

    final JCheckBox checkout = new JCheckBox("Checkout");
    checkout.addActionListener(e -> myCheckout = checkout.isSelected());
    wrapper.add(checkout, gb);
    return wrapper;
  }

  public boolean isCheckout() {
    return myCheckout;
  }

  public String getLocalTarget() {
    return myFolderName.getText();
  }
}
