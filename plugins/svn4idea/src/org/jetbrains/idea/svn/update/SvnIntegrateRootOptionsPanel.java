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

import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.options.ConfigurationException;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import org.jetbrains.idea.svn.dialogs.SelectLocationDialog;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnBundle;
import org.tmatesoft.svn.core.wc.SVNRevision;

public class SvnIntegrateRootOptionsPanel implements SvnPanel{
  private JLabel myMergeURLLabel1;
  private JLabel myMergeURLLabel2;
  private TextFieldWithBrowseButton myMergeText1;
  private TextFieldWithBrowseButton myMergeText2;
  private JLabel myMergeRevisionLabel1;
  private JTextField myMergeRevisionText1;
  private JLabel myMergeRevisionLabel2;
  private JTextField myMergeRevisionText2;

  private JPanel myPanel;
  private final FilePath myRoot;
  private final SvnVcs myVcs;

  public SvnIntegrateRootOptionsPanel(final SvnVcs vcs, FilePath root) {
    myRoot = root;
    myVcs = vcs;

    myMergeText1.setEditable(false);


    myMergeRevisionLabel1.setLabelFor(myMergeRevisionText1);

    myMergeText2.setEditable(false);

    myMergeRevisionLabel2.setLabelFor(myMergeRevisionText2);

    myMergeURLLabel1.setLabelFor(myMergeText1);
    myMergeURLLabel2.setLabelFor(myMergeText2);

    myMergeText1.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        String url = myMergeText1.getText();
        SelectLocationDialog dialog = new SelectLocationDialog(vcs.getProject(), url, null, null, true);
        dialog.show();
        if (dialog.isOK()) {
          url = dialog.getSelectedURL();
          if (url != null) {
            myMergeText1.setText(url);
          }
        }
      }
    });
    myMergeText2.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        String url = myMergeText2.getText();
        SelectLocationDialog dialog = new SelectLocationDialog(vcs.getProject(), url, null, null, true);
        dialog.show();
        if (dialog.isOK()) {
          url = dialog.getSelectedURL();
          if (url != null) {
            myMergeText2.setText(url);
          }
        }
      }
    });

  }

  public void apply(SvnConfiguration conf) throws ConfigurationException {
    final MergeRootInfo rootInfo = conf.getMergeRootInfo(myRoot.getIOFile(), myVcs);
    rootInfo.setUrl1(myMergeRevisionText1.getText());
    rootInfo.setUrl2(myMergeRevisionText2.getText());
    rootInfo.setRevision1(createRevision(myMergeRevisionText1.getText()));
    rootInfo.setRevision2(createRevision(myMergeRevisionText2.getText()));

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

    myMergeRevisionText1.setText(rootInfo.getRevision1().toString());
    myMergeRevisionText1.selectAll();
    myMergeRevisionText2.setText(rootInfo.getRevision2().toString());
    myMergeRevisionText2.selectAll();
    myMergeText1.setText(rootInfo.getUrl1().toString());
    myMergeText2.setText(rootInfo.getUrl2().toString());
  }
}
