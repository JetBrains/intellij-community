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
package org.jetbrains.idea.svn.update;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.branchConfig.SelectBranchPopup;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationManager;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.dialogs.SelectLocationDialog;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.history.SvnRepositoryLocation;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;

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
  @Nullable private SVNURL mySourceUrl;

  public SvnUpdateRootOptionsPanel(FilePath root, final SvnVcs vcs, Collection<FilePath> roots) {
    myRoot = root;
    myVcs = vcs;

    myURLText.setEditable(true);
    myURLText.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        chooseUrl();
      }
    });

    myBranchField.setEditable(false);
    myBranchField.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        chooseBranch();
      }
    });
    myBranchLabel.setLabelFor(myBranchField);
    myUrlLabel.setLabelFor(myURLText);

    myUpdateToSpecificUrl.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myUpdateToSpecificUrl.isSelected()) {
          myURLText.setEnabled(true);
          myBranchField.setEnabled(mySourceUrl != null);
        } else {
          myURLText.setEnabled(false);
          myBranchField.setEnabled(false);
        }
      }
    });

    myRevisionBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (e.getSource() == myRevisionBox) {
          myRevisionText.setEnabled(myRevisionBox.isSelected());
          if (myRevisionBox.isSelected()) {
            if (myRevisionText.getText().trim().isEmpty()) {
              myRevisionText.setText("HEAD");
            }
            myRevisionText.getTextField().selectAll();
            myRevisionText.requestFocus();
          }
        }
      }
    });

    myRevisionText.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final Project project = vcs.getProject();
        // todo check whether ok; rather shoudl be used if checkbox is turned on
        final SvnRepositoryLocation location = new SvnRepositoryLocation(myURLText.getText());
        final SvnChangeList repositoryVersion = SvnSelectRevisionUtil.chooseCommittedChangeList(project, location, myRoot.getVirtualFile());
        if (repositoryVersion != null) {
          myRevisionText.setText(String.valueOf(repositoryVersion.getNumber()));
        }
      }
    });

    myRevisionText.setText(SVNRevision.HEAD.toString());
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
    SelectBranchPopup.show(myVcs.getProject(), myRoot.getVirtualFile(), new SelectBranchPopup.BranchSelectedCallback() {
      public void branchSelected(final Project project, final SvnBranchConfigurationNew configuration, final String url, final long revision) {
        // TODO: It seems that we could reuse configuration passed as parameter to this callback
        SvnBranchConfigurationNew branchConfiguration = getBranchConfiguration();
        String branchRelativeUrl = branchConfiguration != null ? branchConfiguration.getRelativeUrl(mySourceUrl.toString()) : null;

        if (mySourceUrl == null || branchRelativeUrl == null) {
          myBranchField.setText("");
        }
        else {
          try {
            myURLText.setText(SVNURL.parseURIEncoded(url).appendPath(branchRelativeUrl, true).toDecodedString());
          }
          catch (SVNException e) {
            LOG.error(e);
          }
          myBranchField.setText(SVNPathUtil.tail(url));
        }
      }
    }, SvnBundle.message("select.branch.popup.general.title"), myPanel);
  }

  private void chooseUrl() {
    SVNURL selected = SelectLocationDialog.selectLocation(myVcs.getProject(), myURLText.getText());
    if (selected != null) {
      myURLText.setText(selected.toDecodedString());
    }
  }

  public JPanel getPanel() {
    return myPanel;
  }

  @Nullable
  private SVNURL getBranchForUrl(@Nullable SVNURL url) {
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
    SVNURL branchUrl = getBranchForUrl(mySourceUrl);
    if (branchUrl != null) {
      myBranchField.setText(SVNPathUtil.tail(branchUrl.toDecodedString()));
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
        rootInfo.setUrl(SvnUtil.createUrl(myURLText.getText(), false));
      }
      catch (SvnBindException e) {
        throw new ConfigurationException("Invalid url: " + myURLText.getText());
      }
    }

    rootInfo.setUpdateToRevision(myRevisionBox.isSelected());
    final SVNRevision revision = SVNRevision.parse(myRevisionText.getText());
     if (!revision.isValid()) {
      throw new ConfigurationException(SvnBundle.message("invalid.svn.revision.error.message", myRevisionText.getText()));
    }
    rootInfo.setRevision(revision);
  }

  public boolean canApply() {
    return true;
  }
}
