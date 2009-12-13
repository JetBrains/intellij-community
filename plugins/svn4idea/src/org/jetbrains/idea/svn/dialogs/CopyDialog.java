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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.svn.SvnBranchConfiguration;
import org.jetbrains.idea.svn.SvnBranchConfigurationManager;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew;
import org.jetbrains.idea.svn.update.SvnRevisionPanel;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 05.07.2005
 * Time: 23:35:12
 */
public class CopyDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.idea.svn.dialogs.CopyDialog");

  private final File mySrcFile;
  private String mySrcURL;
  private final Project myProject;
  private String myURL;

  private TextFieldWithBrowseButton myToURLText;

  private JTextArea myCommentText;
  private JPanel myTopPanel;
  private JRadioButton myWorkingCopyRadioButton;
  private JRadioButton myRepositoryRadioButton;
  private TextFieldWithBrowseButton myWorkingCopyField;
  private TextFieldWithBrowseButton myRepositoryField;
  private SvnRevisionPanel myRevisionPanel;
  private ComboboxWithBrowseButton myBranchTagBaseComboBox;
  private JTextField myBranchTextField;
  private JRadioButton myBranchOrTagRadioButton;
  private JRadioButton myAnyLocationRadioButton;
  private JButton myProjectButton;
  private JLabel myErrorLabel;

  @NonNls private static final String HELP_ID = "vcs.subversion.branch";
  private SvnBranchConfigurationNew myBranchConfiguration;
  private final VirtualFile mySrcVirtualFile;

  public CopyDialog(final Project project, boolean canBeParent, File file) {
    super(project, canBeParent);
    mySrcFile = file;
    myProject = project;
    setResizable(true);
    setTitle(SvnBundle.message("dialog.title.branch"));
    getHelpAction().setEnabled(true);
    myProjectButton.setIcon(IconLoader.getIcon("/nodes/ideaProject.png"));
    myBranchTagBaseComboBox.setPreferredSize(new Dimension(myBranchTagBaseComboBox.getPreferredSize().width,
                                                           myWorkingCopyField.getPreferredSize().height));

    myWorkingCopyField.addBrowseFolderListener("Select Working Copy Location", "Select Location to Copy From:",
                                               project, new FileChooserDescriptor(false, true, false, false, false, false));
    myWorkingCopyField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        updateControls();
      }
    });
    myRepositoryField.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        String url = SelectLocationDialog.selectLocation(project, myRepositoryField.getText());
        if (url != null) {
          myRepositoryField.setText(url);
        }
      }
    });
    myRepositoryField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        updateToURL();
      }
    });
    myToURLText.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        String url = myToURLText.getText();
        String dstName = SVNPathUtil.tail(mySrcURL);
        dstName = SVNEncodingUtil.uriDecode(dstName);
        try {
          SelectLocationDialog dialog = new SelectLocationDialog(myProject, SVNPathUtil.removeTail(url), SvnBundle.message("label.copy.select.location.dialog.copy.as"), dstName, false);
          dialog.show();
          if (dialog.isOK()) {
            url = dialog.getSelectedURL();
            String name = dialog.getDestinationName();
            url = SVNPathUtil.append(url, name);
            myToURLText.setText(url);
          }
        }
        catch (SVNException e1) {
          Messages.showErrorDialog(project, SvnBundle.message("select.location.invalid.url.message", url),
                                   SvnBundle.message("dialog.title.select.repository.location"));
          //typed text can not be parsed - no information on what repository to use
        }
      }
    });

    VirtualFile srcVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);
    srcVirtualFile = ProjectLevelVcsManager.getInstance(project).getVcsRootFor(srcVirtualFile);
    this.mySrcVirtualFile = srcVirtualFile;

    myRevisionPanel.setRoot(mySrcVirtualFile);
    myRevisionPanel.setProject(myProject);
    myRevisionPanel.setUrlProvider(new SvnRevisionPanel.UrlProvider() {
      public String getUrl() {
        return mySrcURL;
      }
    });
    updateBranchTagBases();

    myRevisionPanel.addChangeListener(new ChangeListener() {
      public void stateChanged(final ChangeEvent e) {
        getOKAction().setEnabled(isOKActionEnabled());
      }
    });

    init();
    ActionListener listener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateControls();
      }
    };
    myWorkingCopyRadioButton.addActionListener(listener);
    myRepositoryRadioButton.addActionListener(listener);
    myBranchOrTagRadioButton.addActionListener(listener);
    myAnyLocationRadioButton.addActionListener(listener);
    updateControls();
    myBranchTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        updateToURL();
      }
    });
    updateToURL();
    myProjectButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myRepositoryField.setText(myBranchConfiguration.getBaseUrl(mySrcURL));
      }
    });
    myBranchTagBaseComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        BranchConfigurationDialog.configureBranches(project, mySrcVirtualFile);
        updateBranchTagBases();
        updateControls();
      }
    });
    myBranchTagBaseComboBox.getComboBox().addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateToURL();
        updateControls();
      }
    });
  }

  private void updateBranchTagBases() {
    try {
      myBranchConfiguration = SvnBranchConfigurationManager.getInstance(myProject).get(mySrcVirtualFile);
      final String[] strings = ArrayUtil.toStringArray(myBranchConfiguration.getBranchUrls());
      myBranchTagBaseComboBox.getComboBox().setModel(new DefaultComboBoxModel(strings));
    }
    catch (VcsException e) {
      LOG.info(e);
      myBranchTagBaseComboBox.setEnabled(false);
    }
  }

  private void updateToURL() {
    if (myBranchConfiguration == null) {
      return;
    }
    String relativeUrl;
    if (myWorkingCopyRadioButton.isSelected()) {
      relativeUrl = myBranchConfiguration.getRelativeUrl(mySrcURL);
    }
    else {
      relativeUrl = myBranchConfiguration.getRelativeUrl(myRepositoryField.getText());
    }

    final Object selectedBranch = myBranchTagBaseComboBox.getComboBox().getSelectedItem();
    if (relativeUrl != null && selectedBranch != null) {
      myToURLText.setText(selectedBranch.toString() + "/" + myBranchTextField.getText() + relativeUrl);
    }
  }

  private void updateControls() {
    myWorkingCopyField.setEnabled(myWorkingCopyRadioButton.isSelected());
    myRepositoryField.setEnabled(myRepositoryRadioButton.isSelected());
    myRevisionPanel.setEnabled(myRepositoryRadioButton.isSelected());
    myProjectButton.setEnabled(myRepositoryRadioButton.isSelected());

    myBranchTagBaseComboBox.setEnabled(myBranchOrTagRadioButton.isSelected());
    myBranchTextField.setEnabled(myBranchOrTagRadioButton.isSelected());
    myToURLText.setEnabled(myAnyLocationRadioButton.isSelected());

    getOKAction().setEnabled(isOKActionEnabled());
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HELP_ID);
  }

  protected void init() {
    super.init();
    SvnVcs vcs = SvnVcs.getInstance(myProject);
    String revStr = "";
    try {
      SVNWCClient client = vcs.createWCClient();
      SVNInfo info = client.doInfo(mySrcFile, SVNRevision.WORKING);
      if (info != null) {
        mySrcURL = info.getURL() == null ? null : info.getURL().toString();
        revStr = String.valueOf(info.getRevision());
        myURL = mySrcURL;
      }
    }
    catch (SVNException e) {
      //
    }
    if (myURL == null) {
      return;
    }
    myWorkingCopyField.setText(mySrcFile.toString());
    myRepositoryField.setText(mySrcURL);
    myToURLText.setText(myURL);
    myRevisionPanel.setRevisionText(revStr);
    updateControls();

    myWorkingCopyRadioButton.setSelected(true);
  }

  public String getComment() {
    return myCommentText.getText();
  }

  public SVNRevision getRevision() {
    if (myWorkingCopyRadioButton.isSelected()) {
      return SVNRevision.WORKING;
    }
    else {
      try {
        return myRevisionPanel.getRevision();
      }
      catch (ConfigurationException e) {
        return SVNRevision.UNDEFINED;
      }
    }
  }

  public String getToURL() {
    return myToURLText.getText();
  }

  protected JComponent createCenterPanel() {
    return myTopPanel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myToURLText;
  }

  public boolean shouldCloseOnCross() {
    return true;
  }

  protected String getDimensionServiceKey() {
    return "svn.copyDialog";
  }

  public boolean isOKActionEnabled() {
    myErrorLabel.setText(" ");
    if (myURL == null) {
      return false;
    }
    if (myBranchOrTagRadioButton.isSelected() && myBranchTagBaseComboBox.getComboBox().getSelectedItem() == null) {
      myErrorLabel.setText(SvnBundle.message("create.branch.no.base.location.error"));
      return false;
    }
    String url = myToURLText.getText();
    if (url != null && url.trim().length() > 0) {
      if (myRepositoryRadioButton.isSelected()) {
        SVNRevision revision;
        try {
          revision = myRevisionPanel.getRevision();
        }
        catch (ConfigurationException e) {
          revision = SVNRevision.UNDEFINED;
        }
        if (!revision.isValid() || revision.isLocal()) {
          myErrorLabel.setText(SvnBundle.message("create.branch.invalid.revision.error", myRevisionPanel.getRevisionText()));
          return false;
        }
        return true;
      }
      else if (myWorkingCopyRadioButton.isSelected()) {
        try {
          SVNWCClient client = SvnVcs.getInstance(myProject).createWCClient();
          SVNInfo info = client.doInfo(mySrcFile, SVNRevision.WORKING);
          mySrcURL = info != null && info.getURL() != null ? info.getURL().toString() : null;
        }
        catch (SVNException e) {
          mySrcURL = null;
        }
        if (mySrcURL == null) {
          myErrorLabel.setText(SvnBundle.message("create.branch.no.working.copy.error", myWorkingCopyField.getText()));
          return false;
        }
        return true;
      }
    }
    return false;
  }

  public boolean isCopyFromWorkingCopy() {
    return myWorkingCopyRadioButton.isSelected();
  }

  public String getCopyFromPath() {
    return myWorkingCopyField.getText();
  }

  public String getCopyFromUrl() {
    return myRepositoryField.getText();
  }
}
