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
package org.jetbrains.idea.svn.branchConfig;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.RootUrlInfo;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.SelectLocationDialog;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.update.SvnRevisionPanel;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;

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
public class CreateBranchOrTagDialog extends DialogWrapper {
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
  private JLabel myUseThisVariantToLabel;
  private JBCheckBox mySwitchOnCreate;

  @NonNls private static final String HELP_ID = "vcs.subversion.branch";
  private SvnBranchConfigurationNew myBranchConfiguration;
  private final VirtualFile mySrcVirtualFile;
  private final String myWcRootUrl;

  public CreateBranchOrTagDialog(final Project project, boolean canBeParent, File file) throws VcsException {
    super(project, canBeParent);
    mySrcFile = file;
    myProject = project;
    setResizable(true);
    setTitle(SvnBundle.message("dialog.title.branch"));
    getHelpAction().setEnabled(true);
    myUseThisVariantToLabel.setBorder(BorderFactory.createEmptyBorder(0,0,10,0));
    myProjectButton.setIcon(AllIcons.Nodes.IdeaProject);
    myBranchTagBaseComboBox.setPreferredSize(new Dimension(myBranchTagBaseComboBox.getPreferredSize().width,
                                                           myWorkingCopyField.getPreferredSize().height));

    myWorkingCopyField.addBrowseFolderListener("Select Working Copy Location", "Select Location to Copy From:",
                                               project, FileChooserDescriptorFactory.createSingleFolderDescriptor());
    myWorkingCopyField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        updateControls();
      }
    });
    myRepositoryField.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        SVNURL url = SelectLocationDialog.selectLocation(project, mySrcURL);
        if (url != null) {
          myRepositoryField.setText(url.toString());
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
        url = SelectLocationDialog.selectCopyDestination(myProject, SVNPathUtil.removeTail(url),
                                                  SvnBundle.message("label.copy.select.location.dialog.copy.as"), dstName, false);
        if (url != null) {
          myToURLText.setText(url);
        }
      }
    });

    VirtualFile srcVirtualFile;
    RootUrlInfo root = SvnVcs.getInstance(myProject).getSvnFileUrlMapping().getWcRootForFilePath(file);
    if (root == null) {
      throw new VcsException("Can not find working copy for file: " + file.getPath());
    }
    srcVirtualFile = root.getVirtualFile();
    if (srcVirtualFile == null) {
      throw new VcsException("Can not find working copy for file: " + file.getPath());
    }
    this.mySrcVirtualFile = srcVirtualFile;
    myWcRootUrl = root.getUrl();

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
        myRepositoryField.setText(myWcRootUrl);
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
    myBranchConfiguration = SvnBranchConfigurationManager.getInstance(myProject).get(mySrcVirtualFile);
    final String[] strings = ArrayUtil.toStringArray(myBranchConfiguration.getBranchUrls());
    myBranchTagBaseComboBox.getComboBox().setModel(new DefaultComboBoxModel(strings));
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

  private String getToURLTextFromBranch() {
    final Object selectedBranch = myBranchTagBaseComboBox.getComboBox().getSelectedItem();
    if (selectedBranch != null) {
      return selectedBranch + "/" + myBranchTextField.getText();
    }
    return null;
  }

  private void updateControls() {
    myWorkingCopyField.setEnabled(myWorkingCopyRadioButton.isSelected());
    mySwitchOnCreate.setEnabled(myWorkingCopyRadioButton.isSelected());
    myRepositoryField.setEnabled(myRepositoryRadioButton.isSelected());
    myRevisionPanel.setEnabled(myRepositoryRadioButton.isSelected());
    myProjectButton.setEnabled(myRepositoryRadioButton.isSelected());

    myBranchTagBaseComboBox.setEnabled(myBranchOrTagRadioButton.isSelected());
    myBranchTextField.setEnabled(myBranchOrTagRadioButton.isSelected());
    myToURLText.setEnabled(myAnyLocationRadioButton.isSelected());
    myUseThisVariantToLabel.setForeground(myWorkingCopyRadioButton.isSelected() ? UIUtil.getActiveTextColor() : UIUtil.getInactiveTextColor());

    getOKAction().setEnabled(isOKActionEnabled());
  }

  @NotNull
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
    Info info = vcs.getInfo(mySrcFile);
    if (info != null) {
      mySrcURL = info.getURL() == null ? null : info.getURL().toString();
      revStr = String.valueOf(info.getRevision());
      myURL = mySrcURL;
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
    if (myBranchOrTagRadioButton.isSelected()) {
      return getToURLTextFromBranch();
    }
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
    String url = getToURL();
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
        Info info = SvnVcs.getInstance(myProject).getInfo(mySrcFile);
        String srcUrl = info != null && info.getURL() != null ? info.getURL().toString() : null;
        if (srcUrl == null) {
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

  public boolean isSwitchOnCreate() {
    return mySwitchOnCreate.isSelected();
  }
}
