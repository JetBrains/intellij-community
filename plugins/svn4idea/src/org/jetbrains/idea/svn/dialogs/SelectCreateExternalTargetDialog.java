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
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.hash.HashSet;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 7/6/12
 * Time: 7:53 PM
 */
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
    getRepositoryBrowser().addChangeListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        if (getOKAction() != null) {
          final String selectedURL = getRepositoryBrowser().getSelectedURL();
          if (myFollowRemoteTarget && selectedURL != null) {
            myFolderName.setText(SVNPathUtil.tail(selectedURL));
          }
          checkEnabled();
        }
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
      setErrorText("Target File Already Exists");
    } else {
      setErrorText(null);
    }
    getOKAction().setEnabled(enabled);
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[] {getOKAction(), getCancelAction()};
  }
  @Override
  protected void doOKAction() {
    mySelectedURL = getRepositoryBrowser().getSelectedURL();
    super.doOKAction();
  }

  public String getSelectedURL() {
    return mySelectedURL;
  }

  protected JPopupMenu createPopup(boolean toolWindow) {
    DefaultActionGroup group = new DefaultActionGroup();
    DefaultActionGroup newGroup = new DefaultActionGroup("_New", true);
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
    checkout.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myCheckout = checkout.isSelected();
      }
    });
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
