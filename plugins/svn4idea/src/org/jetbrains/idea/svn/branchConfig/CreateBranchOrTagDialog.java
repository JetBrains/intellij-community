// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.branchConfig;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.RootUrlInfo;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.dialogs.SelectLocationDialog;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.update.SvnRevisionPanel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;

import static com.intellij.openapi.ui.Messages.showErrorDialog;
import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.SvnUtil.createUrl;
import static org.jetbrains.idea.svn.SvnUtil.removePathTail;
import static org.jetbrains.idea.svn.branchConfig.BranchConfigurationDialog.DECODED_URL_RENDERER;

public class CreateBranchOrTagDialog extends DialogWrapper {
  @NotNull private final File mySrcFile;
  @NotNull private final Url mySrcURL;
  @NotNull private final SvnVcs myVcs;
  @NotNull private final Project myProject;

  private TextFieldWithBrowseButton myToURLText;

  private JTextArea myCommentText;
  private JPanel myTopPanel;
  private JRadioButton myWorkingCopyRadioButton;
  private JRadioButton myRepositoryRadioButton;
  private TextFieldWithBrowseButton myWorkingCopyField;
  private TextFieldWithBrowseButton myRepositoryField;
  private SvnRevisionPanel myRevisionPanel;
  private ComboboxWithBrowseButton myBranchTagBaseComboBox;
  @NotNull private final CollectionComboBoxModel<Url> myBranchTagBaseModel = new CollectionComboBoxModel<>();
  private JTextField myBranchTextField;
  private JRadioButton myBranchOrTagRadioButton;
  private JRadioButton myAnyLocationRadioButton;
  private JButton myProjectButton;
  private JLabel myUseThisVariantToLabel;
  private JBCheckBox mySwitchOnCreate;

  @NonNls private static final String HELP_ID = "vcs.subversion.branch";
  private SvnBranchConfigurationNew myBranchConfiguration;
  private final VirtualFile mySrcVirtualFile;
  private final Url myWcRootUrl;

  public CreateBranchOrTagDialog(@NotNull SvnVcs vcs, @NotNull File file) throws VcsException {
    super(vcs.getProject(), true);
    mySrcFile = file;
    myVcs = vcs;
    myProject = vcs.getProject();
    setResizable(true);
    setTitle(message("dialog.title.branch"));
    myUseThisVariantToLabel.setBorder(JBUI.Borders.emptyBottom(10));
    myProjectButton.setIcon(AllIcons.Nodes.IdeaProject);
    myBranchTagBaseComboBox.setPreferredSize(new Dimension(myBranchTagBaseComboBox.getPreferredSize().width,
                                                           myWorkingCopyField.getPreferredSize().height));

    Info info = myVcs.getInfo(file);
    if (info == null || info.getURL() == null) {
      throw new VcsException("Can not find url for file: " + file.getPath());
    }
    mySrcURL = info.getURL();

    myWorkingCopyField.addBrowseFolderListener("Select Working Copy Location", "Select Location to Copy From:",
                                               myProject, FileChooserDescriptorFactory.createSingleFolderDescriptor());
    myWorkingCopyField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        updateControls();
      }
    });
    myRepositoryField.addActionListener(e -> {
      Url url = SelectLocationDialog.selectLocation(myProject, mySrcURL);
      if (url != null) {
        myRepositoryField.setText(url.toString());
      }
    });
    myRepositoryField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        updateToURL();
      }
    });
    myToURLText.addActionListener(e -> {
      try {
        Url url = createUrl(myToURLText.getText());
        String dstName = mySrcURL.getTail();
        Url destination = SelectLocationDialog
          .selectCopyDestination(myProject, removePathTail(url), message("label.copy.select.location.dialog.copy.as"), dstName, false);

        if (destination != null) {
          myToURLText.setText(destination.toString());
        }
      }
      catch (SvnBindException ex) {
        showErrorDialog(myProject, ex.getMessage(), message("dialog.title.select.repository.location"));
      }
    });

    RootUrlInfo root = myVcs.getSvnFileUrlMapping().getWcRootForFilePath(file);
    if (root == null) {
      throw new VcsException("Can not find working copy for file: " + file.getPath());
    }
    mySrcVirtualFile = root.getVirtualFile();
    myWcRootUrl = root.getUrl();

    myRevisionPanel.setRoot(mySrcVirtualFile);
    myRevisionPanel.setProject(myProject);
    myRevisionPanel.setUrlProvider(() -> mySrcURL);
    myRevisionPanel.setRevisionText(String.valueOf(info.getRevision()));
    updateBranchTagBases();

    init();
    ActionListener listener = e -> updateControls();
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
    myProjectButton.addActionListener(e -> myRepositoryField.setText(myWcRootUrl.toString()));
    //noinspection unchecked
    myBranchTagBaseComboBox.getComboBox().setRenderer(DECODED_URL_RENDERER);
    //noinspection unchecked
    myBranchTagBaseComboBox.getComboBox().setModel(myBranchTagBaseModel);
    myBranchTagBaseComboBox.addActionListener(e -> {
      BranchConfigurationDialog.configureBranches(myProject, mySrcVirtualFile);
      updateBranchTagBases();
      updateControls();
    });
    myBranchTagBaseComboBox.getComboBox().addActionListener(e -> {
      updateToURL();
      updateControls();
    });
  }

  private void updateBranchTagBases() {
    myBranchConfiguration = SvnBranchConfigurationManager.getInstance(myProject).get(mySrcVirtualFile);

    List<Url> branchLocations = myBranchConfiguration.getBranchLocations();
    myBranchTagBaseModel.replaceAll(branchLocations);
    myBranchTagBaseModel.setSelectedItem(getFirstItem(branchLocations));
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
      Url url = null;
      try {
        url = createUrl(myRepositoryField.getText());
      }
      catch (SvnBindException ignored) {
      }
      relativeUrl = url != null ? myBranchConfiguration.getRelativeUrl(url) : null;
    }

    Url selectedBranch = myBranchTagBaseModel.getSelected();
    if (relativeUrl != null && selectedBranch != null) {
      myToURLText.setText(selectedBranch.toString() + "/" + myBranchTextField.getText() + relativeUrl);
    }
  }

  private String getToURLTextFromBranch() {
    Url selectedBranch = myBranchTagBaseModel.getSelected();
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
  }

  @Nullable
  @Override
  protected String getHelpId() {
    return HELP_ID;
  }

  protected void init() {
    super.init();
    myWorkingCopyField.setText(mySrcFile.toString());
    myRepositoryField.setText(mySrcURL.toString());
    myToURLText.setText(mySrcURL.toString());
    updateControls();

    myWorkingCopyRadioButton.setSelected(true);
  }

  public String getComment() {
    return myCommentText.getText();
  }

  public Revision getRevision() {
    if (myWorkingCopyRadioButton.isSelected()) {
      return Revision.WORKING;
    }
    else {
      try {
        return myRevisionPanel.getRevision();
      }
      catch (ConfigurationException e) {
        return Revision.UNDEFINED;
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
    return myBranchTextField;
  }

  protected String getDimensionServiceKey() {
    return "svn.copyDialog";
  }

  @Override
  protected boolean postponeValidation() {
    return false;
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    if (myBranchOrTagRadioButton.isSelected() && myBranchTagBaseModel.getSelected() == null) {
      return new ValidationInfo(message("create.branch.no.base.location.error"), myBranchTagBaseComboBox.getComboBox());
    }

    String url = getToURL();
    if (isEmptyOrSpaces(url)) {
      return new ValidationInfo("Invalid branch url", myAnyLocationRadioButton.isSelected() ? myToURLText.getTextField() : null);
    }
    else if (myRepositoryRadioButton.isSelected()) {
      Revision revision = getRevision();
      if (!revision.isValid() || revision.isLocal()) {
        return new ValidationInfo(message("create.branch.invalid.revision.error"), myRevisionPanel.getRevisionTextField());
      }
    }

    return null;
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
