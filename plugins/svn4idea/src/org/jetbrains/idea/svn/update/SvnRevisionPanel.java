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
package org.jetbrains.idea.svn.update;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.history.SvnRepositoryLocation;
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public class SvnRevisionPanel extends JPanel {
  private JRadioButton mySpecified;
  private JRadioButton myHead;
  private JPanel myPanel;
  private TextFieldWithBrowseButton myRevisionField;
  private Project myProject;
  private UrlProvider myUrlProvider;
  private final List<ChangeListener> myChangeListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private VirtualFile myRoot;

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
        notifyChangeListeners();
      }
    });

    myHead.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myRevisionField.setEnabled(false);
        notifyChangeListeners();
      }
    });

    myRevisionField.getTextField().setColumns(10);
    myRevisionField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        notifyChangeListeners();
      }
    });
  }

  private void chooseRevision() {
    if (myProject != null && myUrlProvider != null) {
      final SvnRepositoryLocation location = new SvnRepositoryLocation(myUrlProvider.getUrl());

      final SvnChangeList version = SvnSelectRevisionUtil.chooseCommittedChangeList(myProject, location, myRoot);
      if (version != null) {
        myRevisionField.setText(String.valueOf(version.getNumber()));
      }
    }
  }

  public void setProject(final Project project) {
    myProject = project;
  }

  public void setRoot(final VirtualFile root) {
    myRoot = root;
  }

  public void setUrlProvider(final UrlProvider urlProvider) {
    myUrlProvider = urlProvider;
  }

  public String getRevisionText() {
    return myHead.isSelected() ? SVNRevision.HEAD.toString() : myRevisionField.getText();
  }

  @NotNull
  public SVNRevision getRevision() throws ConfigurationException {

    if (myHead.isSelected()) return SVNRevision.HEAD;

    final SVNRevision result = SVNRevision.parse(myRevisionField.getText());
    if (!result.isValid()) {
      throw new ConfigurationException(SvnBundle.message("invalid.svn.revision.error.message", myRevisionField.getText()));
    }

    return result;
  }

  public void setRevisionText(String text) {
    myRevisionField.setText(text);
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

  @Override
  public void setEnabled(final boolean enabled) {
    super.setEnabled(enabled);
    if (!enabled) {
      myHead.setEnabled(false);
      mySpecified.setEnabled(false);
      myRevisionField.setEnabled(false);
    }
    else {
      myHead.setEnabled(true);
      mySpecified.setEnabled(true);
      myRevisionField.setEnabled(mySpecified.isSelected());
    }
  }

  public void addChangeListener(ChangeListener listener) {
    myChangeListeners.add(listener);
  }

  public void removeChangeListener(ChangeListener listener) {
    myChangeListeners.remove(listener);
  }

  private void notifyChangeListeners() {
    for(ChangeListener listener: myChangeListeners) {
      listener.stateChanged(new ChangeEvent(this));
    }
  }

  public interface UrlProvider {
    String getUrl();
  }
}
