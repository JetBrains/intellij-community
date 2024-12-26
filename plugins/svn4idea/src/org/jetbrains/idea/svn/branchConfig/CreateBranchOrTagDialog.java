// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.branchConfig;

import com.intellij.ide.ui.ProductIcons;
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
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.RootUrlInfo;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
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
import static com.intellij.vcsUtil.VcsUtil.getFilePath;
import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.SvnUtil.createUrl;
import static org.jetbrains.idea.svn.SvnUtil.removePathTail;
import static org.jetbrains.idea.svn.branchConfig.BranchConfigurationDialog.DECODED_URL_RENDERER;

public class CreateBranchOrTagDialog extends DialogWrapper {
  private final @NotNull File mySrcFile;
  private final @NotNull Url mySrcURL;
  private final @NotNull SvnVcs myVcs;
  private final @NotNull Project myProject;

  private TextFieldWithBrowseButton myToURLText;

  private JTextArea myCommentText;
  private JPanel myTopPanel;
  private JRadioButton myWorkingCopyRadioButton;
  private JRadioButton myRepositoryRadioButton;
  private TextFieldWithBrowseButton myWorkingCopyField;
  private TextFieldWithBrowseButton myRepositoryField;
  private SvnRevisionPanel myRevisionPanel;
  private ComboboxWithBrowseButton myBranchTagBaseComboBox;
  private final @NotNull CollectionComboBoxModel<Url> myBranchTagBaseModel = new CollectionComboBoxModel<>();
  private JTextField myBranchTextField;
  private JRadioButton myBranchOrTagRadioButton;
  private JRadioButton myAnyLocationRadioButton;
  private JButton myProjectButton;
  private JLabel myUseThisVariantToLabel;
  private JBCheckBox mySwitchOnCreate;

  private static final @NonNls String HELP_ID = "vcs.subversion.branch";
  private SvnBranchConfigurationNew myBranchConfiguration;
  private final VirtualFile mySrcVirtualFile;
  private final Url myWcRootUrl;
  private Target mySource;
  private Url myDestination;

  public CreateBranchOrTagDialog(@NotNull SvnVcs vcs, @NotNull File file) throws VcsException {
    super(vcs.getProject(), true);
    mySrcFile = file;
    myVcs = vcs;
    myProject = vcs.getProject();
    setResizable(true);
    setTitle(message("dialog.title.branch"));
    myUseThisVariantToLabel.setBorder(JBUI.Borders.emptyBottom(10));
    mySwitchOnCreate.setBorder(JBUI.Borders.emptyTop(10));
    myProjectButton.setIcon(ProductIcons.getInstance().getProjectIcon());
    myBranchTagBaseComboBox.setPreferredSize(new Dimension(myBranchTagBaseComboBox.getPreferredSize().width,
                                                           myWorkingCopyField.getPreferredSize().height));

    Info info = myVcs.getInfo(file);
    if (info == null || info.getUrl() == null) {
      throw new VcsException(message("error.can.not.find.url.for.file", file.getPath()));
    }
    mySrcURL = info.getUrl();

    myWorkingCopyField.addBrowseFolderListener(myProject, FileChooserDescriptorFactory.createSingleFolderDescriptor()
      .withTitle(message("dialog.title.select.working.copy.location"))
      .withDescription(message("label.select.location.to.copy.from")));
    myWorkingCopyField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(final @NotNull DocumentEvent e) {
        updateSwitchOnCreate();
        updateControls();
      }
    });
    myRepositoryField.addActionListener(e -> {
      Url url = SelectLocationDialog.selectLocation(myProject, mySrcURL);
      if (url != null) {
        myRepositoryField.setText(url.toDecodedString());
      }
    });
    myRepositoryField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(final @NotNull DocumentEvent e) {
        updateToURL();
      }
    });
    myToURLText.addActionListener(e -> {
      try {
        Url url = createUrl(myToURLText.getText(), false);
        String dstName = mySrcURL.getTail();
        Url destination = SelectLocationDialog.selectCopyDestination(myProject, removePathTail(url), dstName);

        if (destination != null) {
          myToURLText.setText(destination.toDecodedString());
        }
      }
      catch (SvnBindException ex) {
        showErrorDialog(myProject, ex.getMessage(), message("dialog.title.select.repository.location"));
      }
    });

    RootUrlInfo root = myVcs.getSvnFileUrlMapping().getWcRootForFilePath(getFilePath(file));
    if (root == null) {
      throw new VcsException(message("error.can.not.find.working.copy.for.file", file.getPath()));
    }
    mySrcVirtualFile = root.getVirtualFile();
    myWcRootUrl = root.getUrl();

    myRevisionPanel.setRoot(mySrcVirtualFile);
    myRevisionPanel.setProject(myProject);
    myRevisionPanel.setUrlProvider(() -> mySrcURL);
    myRevisionPanel.setRevisionText(String.valueOf(info.getRevision()));
    updateBranchTagBases();

    init();
    ActionListener switchOnCreateListener = e -> updateSwitchOnCreate();
    myWorkingCopyRadioButton.addActionListener(switchOnCreateListener);
    myRepositoryRadioButton.addActionListener(switchOnCreateListener);
    ActionListener listener = e -> updateControls();
    myWorkingCopyRadioButton.addActionListener(listener);
    myRepositoryRadioButton.addActionListener(listener);
    myBranchOrTagRadioButton.addActionListener(listener);
    myAnyLocationRadioButton.addActionListener(listener);
    updateControls();
    myBranchTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(final @NotNull DocumentEvent e) {
        updateToURL();
      }
    });
    updateToURL();
    myProjectButton.addActionListener(e -> myRepositoryField.setText(myWcRootUrl.toDecodedString()));
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
    Url url = myWorkingCopyRadioButton.isSelected() ? mySrcURL : getRepositoryFieldUrl();
    String relativeUrl = url != null ? myBranchConfiguration.getRelativeUrl(url) : null;
    Url selectedBranch = myBranchTagBaseModel.getSelected();

    if (relativeUrl != null && selectedBranch != null) {
      try {
        myToURLText.setText(selectedBranch.appendPath(myBranchTextField.getText(), false).appendPath(relativeUrl, false).toDecodedString());
      }
      catch (SvnBindException ignored) {
      }
    }
  }

  private void updateSwitchOnCreate() {
    mySwitchOnCreate.setText(message("checkbox.switch.to.newly.created.branch.or.tag", getSourceFile()));
  }

  private void updateControls() {
    myWorkingCopyField.setEnabled(myWorkingCopyRadioButton.isSelected());
    myRepositoryField.setEnabled(myRepositoryRadioButton.isSelected());
    myRevisionPanel.setEnabled(myRepositoryRadioButton.isSelected());
    myProjectButton.setEnabled(myRepositoryRadioButton.isSelected());

    myBranchTagBaseComboBox.setEnabled(myBranchOrTagRadioButton.isSelected());
    myBranchTextField.setEnabled(myBranchOrTagRadioButton.isSelected());
    myToURLText.setEnabled(myAnyLocationRadioButton.isSelected());
    myUseThisVariantToLabel.setForeground(myWorkingCopyRadioButton.isSelected() ? UIUtil.getActiveTextColor() : NamedColorUtil.getInactiveTextColor());
  }

  @Override
  protected @Nullable String getHelpId() {
    return HELP_ID;
  }

  @Override
  protected void init() {
    super.init();
    myWorkingCopyField.setText(mySrcFile.toString());
    myRepositoryField.setText(mySrcURL.toDecodedString());
    myToURLText.setText(mySrcURL.toDecodedString());
    updateControls();

    myWorkingCopyRadioButton.setSelected(true);
    updateSwitchOnCreate();
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

  private @Nullable Url getRepositoryFieldUrl() {
    try {
      return createUrl(myRepositoryField.getText(), false);
    }
    catch (SvnBindException ignored) {
      return null;
    }
  }

  @Override
  protected JComponent createCenterPanel() {
    return myTopPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myBranchTextField;
  }

  @Override
  protected String getDimensionServiceKey() {
    return "svn.copyDialog";
  }

  @Override
  protected boolean postponeValidation() {
    return false;
  }

  @Override
  protected @Nullable ValidationInfo doValidate() {
    ValidationInfo info = validateSource();
    return info != null ? info : validateDestination();
  }

  private @Nullable ValidationInfo validateSource() {
    if (myRepositoryRadioButton.isSelected()) {
      Url url = getRepositoryFieldUrl();
      if (url == null) {
        return new ValidationInfo(message("dialog.message.invalid.repository.location"), myRepositoryField.getTextField());
      }

      Revision revision = getRevision();
      if (!revision.isValid() || revision.isLocal()) {
        return new ValidationInfo(message("dialog.message.invalid.revision"), myRevisionPanel.getRevisionTextField());
      }

      mySource = Target.on(url, revision);
    }
    else {
      mySource = Target.on(getSourceFile(), getRevision());
    }

    return null;
  }

  private @Nullable ValidationInfo validateDestination() {
    if (myBranchOrTagRadioButton.isSelected()) {
      Url branchLocation = myBranchTagBaseModel.getSelected();
      if (branchLocation == null) {
        return new ValidationInfo(message("dialog.message.no.branch.base.location.selected"), myBranchTagBaseComboBox.getComboBox());
      }

      if (isEmptyOrSpaces(myBranchTextField.getText())) {
        return new ValidationInfo(message("dialog.message.branch.name.is.empty"), myBranchTextField);
      }

      try {
        myDestination = branchLocation.appendPath(myBranchTextField.getText(), false);
      }
      catch (SvnBindException e) {
        return new ValidationInfo(message("dialog.message.invalid.branch.name"), myBranchTextField);
      }
    }
    else {
      try {
        myDestination = createUrl(myToURLText.getText(), false);
      }
      catch (SvnBindException e) {
        return new ValidationInfo(message("dialog.message.invalid.branch.url"), myToURLText.getTextField());
      }
    }

    return null;
  }

  public boolean isSwitchOnCreate() {
    return mySwitchOnCreate.isSelected();
  }

  public @Nullable Target getSource() {
    return mySource;
  }

  public @NotNull File getSourceFile() {
    return myRepositoryRadioButton.isSelected() ? mySrcFile : new File(myWorkingCopyField.getText());
  }

  public @Nullable Url getDestination() {
    return myDestination;
  }
}
