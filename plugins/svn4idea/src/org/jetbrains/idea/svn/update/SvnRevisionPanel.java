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
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.history.SvnCommittedChangesProvider;
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class SvnRevisionPanel extends JPanel {
  private JRadioButton mySpecified;
  private JRadioButton myHead;
  private JPanel myPanel;
  private TextFieldWithBrowseButton myRevisionField;
  private Project myProject;
  private UrlProvider myUrlProvider;

  public SvnRevisionPanel() {
    super(new BorderLayout());
    add(myPanel);
    myHead.setSelected(true);
    myRevisionField.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        chooseRevision();
      }
    });

//    myRevisionField.setEditable(false);
    myRevisionField.setEnabled(false);

    mySpecified.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (mySpecified.isSelected()) {
          if (myRevisionField.getText().trim().length() == 0) {
            myRevisionField.setText("HEAD");
          }
          myRevisionField.setEnabled(true);
        } else {
          myRevisionField.setEnabled(false);
        }
      }
    });

    myHead.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myRevisionField.setEnabled(false);
      }
    });

    myRevisionField.getTextField().setColumns(10);
  }

  private void chooseRevision() {
    if (myProject != null && myUrlProvider != null) {
      final SvnChangeList version =
        AbstractVcsHelper.getInstance(myProject).chooseCommittedChangeList(new SvnCommittedChangesProvider(myProject, myUrlProvider.getUrl()));
      if (version != null) {
        myRevisionField.setText(String.valueOf(version.getNumber()));
      }
    }
  }

  public void setProject(final Project project) {
    myProject = project;
  }

  public void setUrlProvider(final UrlProvider urlProvider) {
    myUrlProvider = urlProvider;
  }

  public String getRevisionText() {
    return myHead.isSelected() ? SVNRevision.HEAD.toString() : myRevisionField.getText();
  }

  public SVNRevision getRevision() throws ConfigurationException {

    if (myHead.isSelected()) return SVNRevision.HEAD;

    final SVNRevision result = SVNRevision.parse(myRevisionField.getText());
    if (!result.isValid()) {
      throw new ConfigurationException(SvnBundle.message("invalid.svn.revision.error.message", myRevisionField.getText()));
    }

    return result;
  }

  public void setRevision(final SVNRevision revision) {
    if (revision == SVNRevision.HEAD) {
      myHead.setSelected(true);
      myRevisionField.setEnabled(false);
    } else {
      myRevisionField.setText(String.valueOf(revision.getNumber()));
      mySpecified.setSelected(true);
      myRevisionField.setEnabled(true);
    }
  }

  interface UrlProvider {
    String getUrl();
  }
}
