// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.update;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

import javax.swing.*;

import static com.intellij.openapi.ui.Messages.showErrorDialog;
import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;
import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.SvnUtil.createUrl;
import static org.jetbrains.idea.svn.dialogs.SelectLocationDialog.selectLocation;

public class SvnIntegrateRootOptionsPanel implements SvnPanel{
  private TextFieldWithBrowseButton myMergeText1;
  private TextFieldWithBrowseButton myMergeText2;
  private JPanel myPanel;
  private SvnRevisionPanel myRevision2;
  private SvnRevisionPanel myRevision1;

  @NotNull private final FilePath myRoot;
  @NotNull private final SvnVcs myVcs;

  public SvnIntegrateRootOptionsPanel(@NotNull SvnVcs vcs, @NotNull FilePath root) {
    myRoot = root;
    myVcs = vcs;

    setupUrlField(myMergeText1);
    setupUrlField(myMergeText2);
    setupRevisionField(myRevision1, myMergeText1);
    setupRevisionField(myRevision2, myMergeText2);
  }

  private void setupUrlField(@NotNull TextFieldWithBrowseButton textField) {
    textField.setEditable(true);
    textField.addActionListener(e -> {
      try {
        Url url = createUrl(textField.getText(), false);
        Url selectedUrl = selectLocation(myVcs.getProject(), url);

        if (selectedUrl != null) {
          textField.setText(selectedUrl.toDecodedString());
        }
      }
      catch (SvnBindException ex) {
        showErrorDialog(myVcs.getProject(), ex.getMessage(), message("dialog.title.select.repository.location"));
      }
    });
  }

  private void setupRevisionField(@NotNull SvnRevisionPanel revisionField, @NotNull TextFieldWithBrowseButton textField) {
    revisionField.setProject(myVcs.getProject());
    revisionField.setRoot(myRoot.getVirtualFile());
    revisionField.setUrlProvider(() -> createUrl(textField.getText(), false));
  }

  @Override
  public void apply(@NotNull SvnConfiguration conf) throws ConfigurationException {
    if (isEmptyOrSpaces(myMergeText1.getText())) {
      myMergeText1.requestFocus();
      throw new ConfigurationException(message("source.url.could.not.be.empty.error.message"));
    }

    if (isEmptyOrSpaces(myMergeText2.getText())) {
      myMergeText2.requestFocus();
      throw new ConfigurationException(message("source.url.could.not.be.empty.error.message"));
    }

    if (myMergeText1.getText().equals(myMergeText2.getText()) && myRevision1.getRevisionText().equals(myRevision2.getRevisionText())) {
      throw new ConfigurationException(message("no.differences.between.sources.error.message"));
    }

    MergeRootInfo rootInfo = conf.getMergeRootInfo(myRoot.getIOFile(), myVcs);
    rootInfo.setUrl1(myMergeText1.getText());
    rootInfo.setUrl2(myMergeText2.getText());
    rootInfo.setRevision1(myRevision1.getRevision());
    rootInfo.setRevision2(myRevision2.getRevision());
  }

  @Override
  public boolean canApply() {
    return !myMergeText1.getText().equals(myMergeText2.getText()) || !myRevision1.getRevisionText().equals(myRevision2.getRevisionText());
  }

  @Override
  @NotNull
  public JPanel getPanel() {
    return myPanel;
  }

  @Override
  public void reset(@NotNull SvnConfiguration config) {
    MergeRootInfo rootInfo = config.getMergeRootInfo(myRoot.getIOFile(), myVcs);
    myRevision1.setRevision(rootInfo.getRevision1());
    myRevision2.setRevision(rootInfo.getRevision2());
    myMergeText1.setText(rootInfo.getUrlString1());
    myMergeText2.setText(rootInfo.getUrlString2());
  }
}
