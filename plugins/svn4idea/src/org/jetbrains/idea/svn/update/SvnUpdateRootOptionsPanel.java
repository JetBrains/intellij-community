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
import org.jetbrains.idea.svn.actions.SelectBranchPopup;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew;
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
  private String mySourceUrl;
  private SVNURL myBranchUrl;
  private final boolean myIsNested;

  public SvnUpdateRootOptionsPanel(FilePath root, final SvnVcs vcs, Collection<FilePath> roots) {
    myRoot = root;
    myVcs = vcs;
    myIsNested = FilePathUtil.isNested(roots, myRoot);

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
          myBranchField.setEnabled((myBranchUrl != null) && (mySourceUrl != null));
          //chooseBranch();
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
            if ("".equals(myRevisionText.getText().trim())) {
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
    myBranchField.setEnabled(myUpdateToSpecificUrl.isSelected() && (myBranchUrl != null) && (mySourceUrl != null));

    final boolean revisionCanBeSpecifiedForRoot = (! myIsNested) || isRevisionCanBeSpecifiedForRoot();
    myRevisionBox.setEnabled(revisionCanBeSpecifiedForRoot);
    myRevisionText.setEnabled(revisionCanBeSpecifiedForRoot);
    myCopyType.setVisible(! revisionCanBeSpecifiedForRoot);
    myCopyType.setFont(myCopyType.getFont().deriveFont(Font.ITALIC));
    myUpdateToSpecificUrl.setEnabled(revisionCanBeSpecifiedForRoot);
  }

  private boolean isRevisionCanBeSpecifiedForRoot() {
    final RootUrlInfo info = myVcs.getSvnFileUrlMapping().getWcRootForFilePath(myRoot.getIOFile());
    if (info != null) {
      final boolean result = (!NestedCopyType.external.equals(info.getType())) && (!NestedCopyType.switched.equals(info.getType()));
      if (! result) {
        myCopyType.setText(info.getType().getName() + " copy");
      }
      return result;
    }
    return true;
  }

  private void chooseBranch() {
    if ((myBranchUrl == null) || (mySourceUrl == null)) {
      myBranchField.setEnabled(false);
      return;
    }
    SelectBranchPopup.show(myVcs.getProject(), myRoot.getVirtualFile(), new SelectBranchPopup.BranchSelectedCallback() {
      public void branchSelected(final Project project, final SvnBranchConfigurationNew configuration, final String url, final long revision) {
        recalculateUrl(url);
        myBranchField.setText(SVNPathUtil.tail(url));
      }
    }, SvnBundle.message("select.branch.popup.general.title"));
  }

  private void recalculateUrl(final String url) {
    // recalculate fields
    try {
      final String newText = SVNURL.parseURIEncoded(url).appendPath(mySourceUrl.substring(myBranchUrl.toString().length()), true).toString();
      myURLText.setText(newText);
    }
    catch (SVNException e) {
      LOG.error(e);
    }
  }

  private void chooseUrl() {
    String selected = SelectLocationDialog.selectLocation(myVcs.getProject(), myURLText.getText());
    if (selected != null) {
      myURLText.setText(selected);
    }
  }

  public JPanel getPanel() {
    return myPanel;
  }

  @Nullable
  private SVNURL getBranchForUrl(final String url) {
    final SvnFileUrlMapping urlMapping = myVcs.getSvnFileUrlMapping();
    final RootUrlInfo rootForFilePath = urlMapping.getWcRootForFilePath(myRoot.getIOFile());
    if (rootForFilePath == null) {
      return null;
    }
    return SvnUtil.getBranchForUrl(myVcs, rootForFilePath.getVirtualFile(), url);
  }

  public void reset(final SvnConfiguration configuration) {
    final UpdateRootInfo rootInfo = configuration.getUpdateRootInfo(myRoot.getIOFile(), myVcs);

    mySourceUrl = rootInfo.getUrlAsString();
    myBranchUrl = getBranchForUrl(mySourceUrl);
    if (myBranchUrl != null) {
      myBranchField.setText(SVNPathUtil.tail(myBranchUrl.toString()));
    }

    myURLText.setText(mySourceUrl);
    myRevisionBox.setSelected(rootInfo.isUpdateToRevision());
    myRevisionText.setText(rootInfo.getRevision().toString());
    myUpdateToSpecificUrl.setSelected(false);
    myRevisionText.setEnabled(myRevisionBox.isSelected());
    myURLText.setEnabled(myUpdateToSpecificUrl.isSelected());
    myBranchField.setEnabled(myUpdateToSpecificUrl.isSelected() && (myBranchUrl != null) && (mySourceUrl != null));
  }

  public void apply(final SvnConfiguration configuration) throws ConfigurationException {
    final UpdateRootInfo rootInfo = configuration.getUpdateRootInfo(myRoot.getIOFile(), myVcs);
    if (myUpdateToSpecificUrl.isSelected()) {
      rootInfo.setUrl(myURLText.getText());
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

/*  private class MyBranchFieldFocusListener implements FocusListener {
    private SvnBranchConfiguration mySvnBranchConfiguration;

    private MyBranchFieldFocusListener() {
      final VirtualFile root = ProjectLevelVcsManager.getInstance(myVcs.getProject()).getVcsRootFor(myRoot);
      if (root != null) {
        try {
          mySvnBranchConfiguration = SvnBranchConfigurationManager.getInstance(myVcs.getProject()).get(root);
        }
        catch (VcsException e) {
          LOG.error(e);
        }
      }
    }

    public void focusGained(final FocusEvent e) {
    }

    public void focusLost(final FocusEvent e) {
      if (mySvnBranchConfiguration != null) {
        String text = myBranchField.getText();
        text = (text == null) ? "" : text.trim();
        if ((myBranchUrl != null) && (mySourceUrl != null) && (text.length() > 0) && (! text.contains("/"))) {
          try {
            final String branch = mySvnBranchConfiguration.getBranchByName(myVcs.getProject(), text);
            if (branch != null) {
              recalculateUrl(branch);
            }
          }
          catch (SVNException exc) {
            LOG.error(exc);
          }
        }
      }
    }
  }*/
}
