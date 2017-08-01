/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNURL;

import javax.swing.*;

import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;
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
      SVNURL selectedUrl = selectLocation(myVcs.getProject(), textField.getText());

      if (selectedUrl != null) {
        textField.setText(selectedUrl.toString());
      }
    });
  }

  private void setupRevisionField(@NotNull SvnRevisionPanel revisionField, @NotNull TextFieldWithBrowseButton textField) {
    revisionField.setProject(myVcs.getProject());
    revisionField.setRoot(myRoot.getVirtualFile());
    revisionField.setUrlProvider(() -> textField.getText());
  }

  public void apply(@NotNull SvnConfiguration conf) throws ConfigurationException {
    if (isEmptyOrSpaces(myMergeText1.getText())) {
      myMergeText1.requestFocus();
      throw new ConfigurationException(SvnBundle.message("source.url.could.not.be.empty.error.message"));
    }

    if (isEmptyOrSpaces(myMergeText2.getText())) {
      myMergeText2.requestFocus();
      throw new ConfigurationException(SvnBundle.message("source.url.could.not.be.empty.error.message"));
    }

    if (myMergeText1.getText().equals(myMergeText2.getText()) && myRevision1.getRevisionText().equals(myRevision2.getRevisionText())) {
      throw new ConfigurationException(SvnBundle.message("no.differences.between.sources.error.message"));
    }

    MergeRootInfo rootInfo = conf.getMergeRootInfo(myRoot.getIOFile(), myVcs);
    rootInfo.setUrl1(myMergeText1.getText());
    rootInfo.setUrl2(myMergeText2.getText());
    rootInfo.setRevision1(myRevision1.getRevision());
    rootInfo.setRevision2(myRevision2.getRevision());
  }

  public boolean canApply() {
    return !myMergeText1.getText().equals(myMergeText2.getText()) || !myRevision1.getRevisionText().equals(myRevision2.getRevisionText());
  }

  @NotNull
  public JPanel getPanel() {
    return myPanel;
  }

  public void reset(@NotNull SvnConfiguration config) {
    MergeRootInfo rootInfo = config.getMergeRootInfo(myRoot.getIOFile(), myVcs);
    myRevision1.setRevision(rootInfo.getRevision1());
    myRevision2.setRevision(rootInfo.getRevision2());
    myMergeText1.setText(rootInfo.getUrlString1());
    myMergeText2.setText(rootInfo.getUrlString2());
  }
}
