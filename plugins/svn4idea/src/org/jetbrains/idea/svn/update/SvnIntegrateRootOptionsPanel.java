/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.SelectLocationDialog;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class SvnIntegrateRootOptionsPanel implements SvnPanel{
  private TextFieldWithBrowseButton myMergeText1;
  private TextFieldWithBrowseButton myMergeText2;

  private JPanel myPanel;
  private final FilePath myRoot;
  private final SvnVcs myVcs;
  private SvnRevisionPanel myRevision2;
  private SvnRevisionPanel myRevision1;
  private JLabel myUrlLabel1;

  public SvnIntegrateRootOptionsPanel(final SvnVcs vcs, FilePath root) {
    myRoot = root;
    myVcs = vcs;

    myMergeText1.setEditable(true);

    myUrlLabel1.setLabelFor(myMergeText1);

    myMergeText2.setEditable(true);

    myMergeText1.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        chooseUrl(myMergeText1, vcs);
      }
    });
    myMergeText2.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        chooseUrl2(vcs);
      }
    });

    myRevision1.setProject(vcs.getProject());
    myRevision2.setProject(vcs.getProject());

    myRevision1.setUrlProvider(new SvnRevisionPanel.UrlProvider() {
      public String getUrl() {
        return myMergeText1.getText();
      }
    });

    myRevision2.setUrlProvider(new SvnRevisionPanel.UrlProvider() {
      public String getUrl() {
        return myMergeText2.getText();
      }
    });

  }

  private boolean chooseUrl2(final SvnVcs vcs) {
    return chooseUrl(myMergeText2, vcs);
  }

  private boolean chooseUrl(final TextFieldWithBrowseButton textField, final SvnVcs vcs) {
    String url = textField.getText();
    SelectLocationDialog dialog = new SelectLocationDialog(vcs.getProject(), url, null, null, true);
    dialog.show();
    if (dialog.isOK()) {
      url = dialog.getSelectedURL();
      if (url != null) {
        textField.setText(url);
        return true;
      } else {
        return false;
      }
    } else {
      return false;
    }
  }

  public void apply(SvnConfiguration conf) throws ConfigurationException {

    if (myMergeText1.getText().trim().length() == 0) {
      myMergeText1.getTextField().requestFocus();
      throw new ConfigurationException(SvnBundle.message("source.url.could.not.be.empty.error.message"));
    }

    if (myMergeText2.getText().trim().length() == 0) {
      myMergeText2.getTextField().requestFocus();
      throw new ConfigurationException(SvnBundle.message("source.url.could.not.be.empty.error.message"));
    }

    if (myMergeText1.getText().equals(myMergeText2.getText()) && myRevision1.getRevisionText().equals(myRevision2.getRevisionText())) {
      throw new ConfigurationException(SvnBundle.message("no.differences.between.sources.error.message"));
    }

    final MergeRootInfo rootInfo = conf.getMergeRootInfo(myRoot.getIOFile(), myVcs);
    rootInfo.setUrl1(myMergeText1.getText());
    rootInfo.setUrl2(myMergeText2.getText());
    rootInfo.setRevision1(myRevision1.getRevision());
    rootInfo.setRevision2(myRevision2.getRevision());

  }

  public boolean canApply() {
    return !myMergeText1.getText().equals(myMergeText2.getText()) || !myRevision1.getRevisionText().equals(myRevision2.getRevisionText());
  }

  public JPanel getPanel() {
    return myPanel;
  }

  public void reset(SvnConfiguration config) {
    final MergeRootInfo rootInfo = config.getMergeRootInfo(myRoot.getIOFile(), myVcs);
    myRevision1.setRevision(rootInfo.getRevision1());
    myRevision2.setRevision(rootInfo.getRevision2());
    myMergeText1.setText(rootInfo.getUrlString1());
    myMergeText2.setText(rootInfo.getUrlString2());
  }
}
