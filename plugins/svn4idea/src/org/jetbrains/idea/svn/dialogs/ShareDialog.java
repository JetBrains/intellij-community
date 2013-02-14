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
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.ui.EditChangelistSupport;
import com.intellij.openapi.vcs.ui.CommitMessage;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
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
  private CommitMessage myCommitMessage;
  private JComponent myPrefferedFocused;

  public ShareDialog(Project project, final String name) {
    super(project, false, "Point to repository location");
    myName = name;
    updateOptionsTexts(null);
    myExisting.setToolTipText(ourExisting);
    mySameNameAsLocal.setToolTipText(MessageFormat.format(ourProject, myName));
    myTrunk.setToolTipText(MessageFormat.format(ourStandart, myName));

    myRepositoriesLabel.setFont(myRepositoriesLabel.getFont().deriveFont(Font.BOLD));
    myPrefferedFocused = (JComponent) getRepositoryBrowser().getPreferredFocusedComponent();
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
    getRepositoryBrowser().getPreferredFocusedComponent().addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        myPrefferedFocused = (JComponent)getRepositoryBrowser().getPreferredFocusedComponent();
      }
    });
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
  public JComponent getPreferredFocusedComponent() {
    return myPrefferedFocused;
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[] {getOKAction(), getCancelAction(), getHelpAction()};
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
    ActionPopupMenu menu = createShortPopupForRepositoryDialog(getRepositoryBrowser());
    return menu.getComponent();
  }

  public static ActionPopupMenu createShortPopupForRepositoryDialog(RepositoryBrowserComponent browserComponent) {
    DefaultActionGroup group = new DefaultActionGroup();
    DefaultActionGroup newGroup = new DefaultActionGroup("_New", true);
    newGroup.add(new AddLocationAction(browserComponent));
    newGroup.add(new MkDirAction(browserComponent));
    group.add(newGroup);
    group.addSeparator();
    group.add(new RefreshAction(browserComponent));
    group.add(new DiscardLocationAction(browserComponent));
    group.add(new DeleteAction(browserComponent));
    return ActionManager.getInstance().createActionPopupMenu("", group);
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
    gb.weightx = 1;
    gb.weighty = 0;
    gb.fill = GridBagConstraints.HORIZONTAL;
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

  public String getCommitText() {
    return myCommitMessage.getComment();
  }

  private JComponent createFolderPanel() {
    final Project project = myVCS.getProject();
    myCommitMessage = new CommitMessage(project) {
      @Override
      public Dimension getPreferredSize() {
        final Dimension superValue = super.getPreferredSize();
        return new Dimension(superValue.width, superValue.height > 90 ? superValue.height : 90);
      }

      @Override
      public void addNotify() {
        super.addNotify();
        myCommitMessage.getEditorField().getFocusTarget().addFocusListener(new FocusAdapter() {
          @Override
          public void focusGained(FocusEvent e) {
            myPrefferedFocused = myCommitMessage.getEditorField();
          }
        });
      }
    };
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

    ++ gb.gridy;
    gb.gridx = 0;
    gb.insets.top = 1;
    gb.insets.left = 1;
    gb.weightx = 1;
    gb.fill = GridBagConstraints.HORIZONTAL;

    final LocalChangeList list = ChangeListManager.getInstance(project).getDefaultChangeList();
    String text = list.getComment();
    text = StringUtil.isEmptyOrSpaces(text) ? (list.hasDefaultName() ? "" : list.getName()) : text;
    myCommitMessage.setText(text);
    panel.add(myCommitMessage, gb);
    myCommitMessage.setSeparatorText("Commit Comment Prefix");
    for (EditChangelistSupport support : Extensions.getExtensions(EditChangelistSupport.EP_NAME, project)) {
      support.installSearch(myCommitMessage.getEditorField(), myCommitMessage.getEditorField());
    }

    myTrunk.setSelected(true);

    return panel;
  }

  public static enum ShareTarget {
    useSelected,
    useProjectName,
    trunkUnderProjectName
  }
}
