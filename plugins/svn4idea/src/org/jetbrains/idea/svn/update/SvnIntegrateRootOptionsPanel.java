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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.versionBrowser.RepositoryVersion;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.SelectLocationDialog;
import org.jetbrains.idea.svn.history.SvnVersionsProvider;
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class SvnIntegrateRootOptionsPanel implements SvnPanel{
  private TextFieldWithBrowseButton myMergeText1;
  private TextFieldWithBrowseButton myMergeText2;

  private JPanel myPanel;
  private final FilePath myRoot;
  private final SvnVcs myVcs;
  private TextFieldWithBrowseButton myRevision2;
  private TextFieldWithBrowseButton myRevision1;
  private JLabel myUrlLabel1;

  public SvnIntegrateRootOptionsPanel(final SvnVcs vcs, FilePath root) {
    myRoot = root;
    myVcs = vcs;

    myMergeText1.setEditable(false);

    myUrlLabel1.setLabelFor(myMergeText1);

    myMergeText2.setEditable(false);

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

    myRevision1.setEditable(false);
    myRevision2.setEditable(false);


    myRevision1.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        chooseRevision(myRevision1, myMergeText1.getText(), vcs.getProject());
      }

    });


    myRevision2.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        chooseRevision(myRevision2, myMergeText2.getText(), vcs.getProject());
      }

    });

  }

  private void chooseRevision(final TextFieldWithBrowseButton revisionField, String url, final Project project) {
    final RepositoryVersion revision = AbstractVcsHelper.getInstance(project).chooseRepositoryVersion(new SvnVersionsProvider(project, url));
    if (revision != null) {
      revisionField.setText(String.valueOf(revision.getNumber()));
    }
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
    final MergeRootInfo rootInfo = conf.getMergeRootInfo(myRoot.getIOFile(), myVcs);
    rootInfo.setUrl1(myMergeText1.getText());
    rootInfo.setUrl2(myMergeText2.getText());
    rootInfo.setRevision1(createRevision(myRevision1.getText()));
    rootInfo.setRevision2(createRevision(myRevision1.getText()));

  }

  private SVNRevision createRevision(final String text) throws ConfigurationException {
    final SVNRevision result = SVNRevision.parse(text);
    if (!result.isValid()) {
      throw new ConfigurationException(SvnBundle.message("invalid.svn.revision.error.message", text));
    }

    return result;
  }

  public JPanel getPanel() {
    return myPanel;
  }

  public void reset(SvnConfiguration config) {
    final MergeRootInfo rootInfo = config.getMergeRootInfo(myRoot.getIOFile(), myVcs);
    myRevision1.setText(rootInfo.getRevision1().toString());
    myRevision2.setText(rootInfo.getRevision2().toString());
    myMergeText1.setText(rootInfo.getUrlString1());
    myMergeText2.setText(rootInfo.getUrlString2());

    if (myMergeText1.getText().equals(myMergeText2.getText())) {
      myMergeText2.setEnabled(false);
    }
  }
}
