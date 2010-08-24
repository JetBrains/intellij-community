/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.*;
import java.text.MessageFormat;

public class ShareDialog extends RepositoryBrowserDialog {
  private String mySelectedURL;

  private static final String ourExisting = "At selected repository location";
  private static final String ourProject = "In new \"{0}\" folder at selected repository location";
  private static final String ourStandart = "In new \"{0}/trunk\" folder at selected repository location";

  private final String myName;
  private JRadioButton myExisting;
  private JRadioButton mySameNameAsLocal;
  private JRadioButton myTrunk;
  private JCheckBox myCreateStandard;

  public ShareDialog(Project project, final String name) {
    super(project, false, "Point to repository location");
    myName = name;
    updateOptionsTexts(null);
    myExisting.setToolTipText(ourExisting);
    mySameNameAsLocal.setToolTipText(MessageFormat.format(ourProject, myName));
    myTrunk.setToolTipText(MessageFormat.format(ourStandart, myName));

    myRepositoriesLabel.setFont(myRepositoriesLabel.getFont().deriveFont(Font.BOLD));
  }

  public void init() {
    super.init();
    setTitle("Select Share Target");
    setOKButtonText("Share");
    getRepositoryBrowser().addChangeListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        if (getOKAction() != null) {
          final String selectedURL = getRepositoryBrowser().getSelectedURL();
          updateOptionsTexts(selectedURL);
          getOKAction().setEnabled(selectedURL != null);
        }
      }
    });
    getOKAction().setEnabled(getRepositoryBrowser().getSelectedURL() != null);
    ((RepositoryTreeModel) getRepositoryBrowser().getRepositoryTree().getModel()).setShowFiles(false);
  }

  private void updateOptionsTexts(String selectedURL) {
    final boolean enabled = selectedURL != null;
    if (! enabled) {
      myExisting.setText(ourExisting);
      mySameNameAsLocal.setText(MessageFormat.format(ourProject, myName));
      myTrunk.setText(MessageFormat.format(ourStandart, myName));
    } else {
      myExisting.setText(selectedURL);
      final String corrected = selectedURL.endsWith("/") || selectedURL.endsWith("\\") ? selectedURL : (selectedURL + "/");
      mySameNameAsLocal.setText(corrected + myName);
      myTrunk.setText(corrected + myName + "/trunk");
    }

    myExisting.setEnabled(enabled);
    mySameNameAsLocal.setEnabled(enabled);
    myTrunk.setEnabled(enabled);
    myCreateStandard.setEnabled(enabled && myTrunk.isSelected());
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
    newGroup.add(new AddLocationAction());
    newGroup.add(new MkDirAction());
    group.add(newGroup);
    group.addSeparator();
    group.add(new RefreshAction());
    group.add(new DiscardLocationAction());
    ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu("", group);
    return menu.getComponent();
  }

  @Override
  public JComponent createCenterPanel() {
    final JComponent repositoryPanel = super.createCenterPanel();
    final JPanel wrapper = new JPanel(new GridBagLayout());
    final GridBagConstraints gb = new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                                                         new Insets(1, 1, 1, 1), 0, 0);
    gb.weightx = 1;
    gb.weighty = 1;
    wrapper.add(repositoryPanel, gb);
    ++ gb.gridy;
    gb.fill = GridBagConstraints.NONE;
    gb.weightx = 0;
    gb.weighty = 0;
    wrapper.add(createFolderPanel(), gb);
    return wrapper;
  }

  public ShareTarget getShareTarget() {
    if (myExisting.isSelected()) {
      return ShareTarget.useSelected;
    }
    if (mySameNameAsLocal.isSelected()) {
      return ShareTarget.useProjectName;
    }
    return ShareTarget.trunkUnderProjectName;
  }

  public boolean createStandardStructure() {
    return myCreateStandard.isSelected();
  }

  private JComponent createFolderPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final GridBagConstraints gb = new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                         new Insets(1, 1, 1, 1), 0, 0);
    gb.insets.top = 5;
    final JLabel label = new JLabel("Define share target");
    label.setFont(label.getFont().deriveFont(Font.BOLD));
    panel.add(label, gb);
    gb.insets.top = 1;

    final ButtonGroup bg = new ButtonGroup();
    myExisting = new JRadioButton();
    mySameNameAsLocal = new JRadioButton();
    myTrunk = new JRadioButton();
    
    bg.add(myExisting);
    bg.add(mySameNameAsLocal);
    bg.add(myTrunk);

    gb.insets.top = 1;
    ++ gb.gridy;
    panel.add(myExisting, gb);
    ++ gb.gridy;
    panel.add(mySameNameAsLocal, gb);
    ++ gb.gridy;
    gb.insets.top = 5;
    panel.add(myTrunk, gb);
    myCreateStandard = new JCheckBox("Create /tags and /branches");
    myTrunk.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        myCreateStandard.setEnabled(myTrunk.isSelected());
      }
    });
    myCreateStandard.setSelected(true);
    ++ gb.gridy;
    gb.insets.top = 0;
    gb.insets.left = 10;
    panel.add(myCreateStandard, gb);

    myTrunk.setSelected(true);

    return panel;
  }

  public static enum ShareTarget {
    useSelected,
    useProjectName,
    trunkUnderProjectName
  }
}
