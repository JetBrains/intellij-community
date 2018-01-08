// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.update;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.wm.IdeFocusManager;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.branchConfig.SelectBranchPopup;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationManager;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.dialogs.SelectLocationDialog;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.history.SvnRepositoryLocation;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

import static com.intellij.openapi.ui.Messages.showErrorDialog;
import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.SvnUtil.append;
import static org.jetbrains.idea.svn.SvnUtil.createUrl;

public class SvnUpdateRootOptionsPanel implements SvnPanel{
  private final static Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.update.SvnUpdateRootOptionsPanel.SvnUpdateRootOptionsPanel");
  private TextFieldWithBrowseButton myURLText;
  private JCheckBox myRevisionBox;
  private TextFieldWithBrowseButton myRevisionText;

  private final SvnVcs myVcs;
  private JPanel myPanel;
  private final FilePath myRoot;
  private JCheckBox myUpdateToSpecificUrl;
  private TextFieldWithBrowseButton myBranchField;
  private JLabel myBranchLabel;
  private JLabel myUrlLabel;
  private JLabel myCopyType;
  @Nullable private Url mySourceUrl;

  public SvnUpdateRootOptionsPanel(FilePath root, final SvnVcs vcs, Collection<FilePath> roots) {
    myRoot = root;
    myVcs = vcs;

    myURLText.setEditable(true);
    myURLText.addActionListener(e -> chooseUrl());

    myBranchField.setEditable(false);
    myBranchField.addActionListener(e -> chooseBranch());
    myBranchLabel.setLabelFor(myBranchField);
    myUrlLabel.setLabelFor(myURLText);

    myUpdateToSpecificUrl.addActionListener(e -> {
      if (myUpdateToSpecificUrl.isSelected()) {
        myURLText.setEnabled(true);
        myBranchField.setEnabled(mySourceUrl != null);
      }
      else {
        myURLText.setEnabled(false);
        myBranchField.setEnabled(false);
      }
    });

    myRevisionBox.addActionListener(e -> {
      if (e.getSource() == myRevisionBox) {
        myRevisionText.setEnabled(myRevisionBox.isSelected());
        if (myRevisionBox.isSelected()) {
          if (myRevisionText.getText().trim().isEmpty()) {
            myRevisionText.setText("HEAD");
          }
          myRevisionText.getTextField().selectAll();
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
            IdeFocusManager.getGlobalInstance().requestFocus(myRevisionText, true);
          });
        }
      }
    });

    myRevisionText.addActionListener(e -> {
      final Project project = vcs.getProject();
      // todo check whether ok; rather shoudl be used if checkbox is turned on
      final SvnRepositoryLocation location = new SvnRepositoryLocation(myURLText.getText());
      final SvnChangeList repositoryVersion = SvnSelectRevisionUtil.chooseCommittedChangeList(project, location, myRoot.getVirtualFile());
      if (repositoryVersion != null) {
        myRevisionText.setText(String.valueOf(repositoryVersion.getNumber()));
      }
    });

    myRevisionText.setText(Revision.HEAD.toString());
    myRevisionText.getTextField().selectAll();
    myRevisionText.setEnabled(myRevisionBox.isSelected());
    myURLText.setEnabled(myUpdateToSpecificUrl.isSelected());
    myBranchField.setEnabled(myUpdateToSpecificUrl.isSelected() && (mySourceUrl != null));

    final boolean revisionCanBeSpecifiedForRoot = !FilePathUtil.isNested(roots, myRoot) || isRevisionCanBeSpecifiedForRoot();
    myRevisionBox.setEnabled(revisionCanBeSpecifiedForRoot);
    myRevisionText.setEnabled(revisionCanBeSpecifiedForRoot);
    myCopyType.setVisible(! revisionCanBeSpecifiedForRoot);
    myCopyType.setFont(myCopyType.getFont().deriveFont(Font.ITALIC));
    myUpdateToSpecificUrl.setEnabled(revisionCanBeSpecifiedForRoot);
  }

  private boolean isRevisionCanBeSpecifiedForRoot() {
    final RootUrlInfo info = myVcs.getSvnFileUrlMapping().getWcRootForFilePath(myRoot.getIOFile());
    if (info != null) {
      boolean isExternalOrSwitched = NestedCopyType.external.equals(info.getType()) || NestedCopyType.switched.equals(info.getType());
      if (isExternalOrSwitched) {
        myCopyType.setText(info.getType().getName() + " copy");
      }
      return !isExternalOrSwitched;
    }
    return true;
  }

  private void chooseBranch() {
    if (mySourceUrl == null) {
      myBranchField.setEnabled(false);
      return;
    }
    SelectBranchPopup.show(myVcs.getProject(), myRoot.getVirtualFile(), (project, configuration, url, revision) -> {
      // TODO: It seems that we could reuse configuration passed as parameter to this callback
      SvnBranchConfigurationNew branchConfiguration = getBranchConfiguration();
      String branchRelativeUrl = branchConfiguration != null ? branchConfiguration.getRelativeUrl(mySourceUrl.toString()) : null;

      if (mySourceUrl == null || branchRelativeUrl == null) {
        myBranchField.setText("");
      }
      else {
        try {
          myURLText.setText(append(createUrl(url), branchRelativeUrl, true).toDecodedString());
        }
        catch (SvnBindException e) {
          LOG.error(e);
        }
        myBranchField.setText(Url.tail(url));
      }
    }, message("select.branch.popup.general.title"), myPanel);
  }

  private void chooseUrl() {
    try {
      Url url = createUrl(myURLText.getText(), false);
      Url selected = SelectLocationDialog.selectLocation(myVcs.getProject(), url);
      if (selected != null) {
        myURLText.setText(selected.toDecodedString());
      }
    }
    catch (SvnBindException e) {
      showErrorDialog(myVcs.getProject(), e.getMessage(), message("dialog.title.select.repository.location"));
    }
  }

  public JPanel getPanel() {
    return myPanel;
  }

  @Nullable
  private Url getBranchForUrl(@Nullable Url url) {
    final RootUrlInfo rootInfo = myVcs.getSvnFileUrlMapping().getWcRootForFilePath(myRoot.getIOFile());

    return rootInfo != null && url != null ? SvnUtil.getBranchForUrl(myVcs, rootInfo.getVirtualFile(), url) : null;
  }

  @Nullable
  private SvnBranchConfigurationNew getBranchConfiguration() {
    final RootUrlInfo rootInfo = myVcs.getSvnFileUrlMapping().getWcRootForFilePath(myRoot.getIOFile());

    return rootInfo != null ? SvnBranchConfigurationManager.getInstance(myVcs.getProject()).get(rootInfo.getVirtualFile()) : null;
  }

  public void reset(final SvnConfiguration configuration) {
    final UpdateRootInfo rootInfo = configuration.getUpdateRootInfo(myRoot.getIOFile(), myVcs);

    mySourceUrl = rootInfo.getUrl();
    Url branchUrl = getBranchForUrl(mySourceUrl);
    if (branchUrl != null) {
      myBranchField.setText(branchUrl.getTail());
    }

    myURLText.setText(mySourceUrl != null ? mySourceUrl.toDecodedString() : "");
    myRevisionBox.setSelected(rootInfo.isUpdateToRevision());
    myRevisionText.setText(rootInfo.getRevision().toString());
    myUpdateToSpecificUrl.setSelected(false);
    myRevisionText.setEnabled(myRevisionBox.isSelected());
    myURLText.setEnabled(myUpdateToSpecificUrl.isSelected());
    myBranchField.setEnabled(myUpdateToSpecificUrl.isSelected() && (mySourceUrl != null));
  }

  public void apply(final SvnConfiguration configuration) throws ConfigurationException {
    final UpdateRootInfo rootInfo = configuration.getUpdateRootInfo(myRoot.getIOFile(), myVcs);
    if (myUpdateToSpecificUrl.isSelected()) {
      try {
        rootInfo.setUrl(createUrl(myURLText.getText(), false));
      }
      catch (SvnBindException e) {
        throw new ConfigurationException("Invalid url: " + myURLText.getText());
      }
    }

    rootInfo.setUpdateToRevision(myRevisionBox.isSelected());
    final Revision revision = Revision.parse(myRevisionText.getText());
     if (!revision.isValid()) {
       throw new ConfigurationException(message("invalid.svn.revision.error.message", myRevisionText.getText()));
    }
    rootInfo.setRevision(revision);
  }

  public boolean canApply() {
    return true;
  }
}
