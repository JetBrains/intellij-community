// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.ui.EditChangelistSupport;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

import static com.intellij.openapi.ui.Messages.showErrorDialog;
import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.branchConfig.DefaultBranchConfig.TRUNK_NAME;

public class ShareDialog extends RepositoryBrowserDialog {
  private String mySelectedURL;
  private final @NlsSafe @NotNull String myName;
  private JRadioButton myExisting;
  private JRadioButton mySameNameAsLocal;
  private JRadioButton myTrunk;
  private JBCheckBox myCreateStandard;
  private CommitMessage myCommitMessage;
  private JComponent myPrefferedFocused;

  public ShareDialog(Project project, @NlsSafe @NotNull String name) {
    super(project, false, message("label.point.to.repository.location"));
    myName = name;

    myExisting.setToolTipText(message("radio.share.target.at.selected.repository.location"));
    mySameNameAsLocal.setToolTipText(message("radio.share.target.in.new.folder", myName));
    myTrunk.setToolTipText(message("radio.share.target.in.new.folder", myName + "/" + TRUNK_NAME));
    updateTargetOptions(false);

    myRepositoriesLabel.setFont(myRepositoriesLabel.getFont().deriveFont(Font.BOLD));
    myPrefferedFocused = (JComponent)getRepositoryBrowser().getPreferredFocusedComponent();
  }

  @Override
  public void init() {
    super.init();
    setTitle(message("dialog.title.select.share.target"));
    setOKButtonText(message("button.share"));
    getRepositoryBrowser().addChangeListener(e -> {
      Url url = getRepositoryBrowser().getSelectedSVNURL();
      boolean enabled = url != null && setTarget(url);

      updateTargetOptions(enabled);
      getOKAction().setEnabled(enabled);
    });
    getOKAction().setEnabled(getRepositoryBrowser().getSelectedURL() != null);
    ((RepositoryTreeModel)getRepositoryBrowser().getRepositoryTree().getModel()).setShowFiles(false);
    getRepositoryBrowser().getPreferredFocusedComponent().addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        myPrefferedFocused = (JComponent)getRepositoryBrowser().getPreferredFocusedComponent();
      }
    });
  }

  private boolean setTarget(@NotNull Url url) {
    try {
      myExisting.setText(url.toDecodedString());
      mySameNameAsLocal.setText(url.appendPath(myName, false).toDecodedString());
      myTrunk.setText(url.appendPath(myName, false).appendPath(TRUNK_NAME, false).toDecodedString());

      return true;
    }
    catch (SvnBindException e) {
      showErrorDialog(myVCS.getProject(), e.getMessage(), message("dialog.title.error"));

      return false;
    }
  }

  private void updateTargetOptions(boolean enabled) {
    if (!enabled) {
      myExisting.setText(myExisting.getToolTipText());
      mySameNameAsLocal.setText(mySameNameAsLocal.getToolTipText());
      myTrunk.setText(myTrunk.getToolTipText());
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

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
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
    ActionPopupMenu menu = createShortPopupForRepositoryDialog(getRepositoryBrowser());
    return menu.getComponent();
  }

  public static ActionPopupMenu createShortPopupForRepositoryDialog(RepositoryBrowserComponent browserComponent) {
    DefaultActionGroup group = new DefaultActionGroup();
    DefaultActionGroup newGroup = DefaultActionGroup.createPopupGroup(ActionsBundle.messagePointer("group.NewGroup.text"));
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
                                                         JBUI.insets(1), 0, 0);
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

  @NotNull
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

  @NotNull
  public String getCommitText() {
    return myCommitMessage.getComment();
  }

  private JComponent createFolderPanel() {
    final Project project = myVCS.getProject();
    myCommitMessage = new CommitMessage(project) {
      @Override
      public Dimension getPreferredSize() {
        final Dimension superValue = super.getPreferredSize();
        return new Dimension(superValue.width, Math.max(superValue.height, 90));
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
    Disposer.register(getDisposable(), myCommitMessage);
    final JPanel panel = new JPanel(new GridBagLayout());
    final GridBagConstraints gb = new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                         JBUI.insets(1), 0, 0);
    gb.insets.top = 5;
    final JBLabel label = new JBLabel(message("label.define.share.target"));
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
    myCreateStandard = new JBCheckBox(message("checkbox.create.tags.branches"));
    myTrunk.addChangeListener(e -> myCreateStandard.setEnabled(myTrunk.isSelected()));
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
    myCommitMessage.setSeparatorText(message("separator.commit.comment.prefix"));
    for (EditChangelistSupport support : EditChangelistSupport.EP_NAME.getExtensions(project)) {
      support.installSearch(myCommitMessage.getEditorField(), myCommitMessage.getEditorField());
    }

    myTrunk.setSelected(true);

    return panel;
  }

  public enum ShareTarget {
    useSelected,
    useProjectName,
    trunkUnderProjectName
  }
}
