// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.update;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.history.SvnRepositoryLocation;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.List;

import static com.intellij.openapi.ui.Messages.showErrorDialog;
import static org.jetbrains.idea.svn.SvnBundle.message;

public class SvnRevisionPanel extends JPanel {
  private JRadioButton mySpecified;
  private JRadioButton myHead;
  private JPanel myPanel;
  private TextFieldWithBrowseButton myRevisionField;
  private Project myProject;
  private ThrowableComputable<Url, SvnBindException> myUrlProvider;
  private final List<ChangeListener> myChangeListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private VirtualFile myRoot;

  public SvnRevisionPanel() {
    super(new BorderLayout());
    add(myPanel);
    myHead.setSelected(true);
    myRevisionField.addActionListener(e -> chooseRevision());

//    myRevisionField.setEditable(false);
    myRevisionField.setEnabled(false);

    mySpecified.addActionListener(e -> {
      if (mySpecified.isSelected()) {
        if (myRevisionField.getText().trim().length() == 0) {
          myRevisionField.setText("HEAD");
        }
        myRevisionField.setEnabled(true);
      }
      else {
        myRevisionField.setEnabled(false);
      }
      notifyChangeListeners();
    });

    myHead.addActionListener(e -> {
      myRevisionField.setEnabled(false);
      notifyChangeListeners();
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
      try {
        SvnRepositoryLocation location = new SvnRepositoryLocation(myUrlProvider.compute().toString());
        SvnChangeList version = SvnSelectRevisionUtil.chooseCommittedChangeList(myProject, location, myRoot);
        if (version != null) {
          myRevisionField.setText(String.valueOf(version.getNumber()));
        }
      }
      catch (SvnBindException e) {
        showErrorDialog(myProject, e.getMessage(), message("error.cannot.load.revisions"));
      }
    }
  }

  public void setProject(final Project project) {
    myProject = project;
  }

  public void setRoot(final VirtualFile root) {
    myRoot = root;
  }

  public void setUrlProvider(@Nullable ThrowableComputable<Url, SvnBindException> urlProvider) {
    myUrlProvider = urlProvider;
  }

  public String getRevisionText() {
    return myHead.isSelected() ? Revision.HEAD.toString() : myRevisionField.getText();
  }

  @NotNull
  public Revision getRevision() throws ConfigurationException {

    if (myHead.isSelected()) return Revision.HEAD;

    final Revision result = Revision.parse(myRevisionField.getText());
    if (!result.isValid()) {
      throw new ConfigurationException(message("invalid.svn.revision.error.message", myRevisionField.getText()));
    }

    return result;
  }

  public void setRevisionText(String text) {
    myRevisionField.setText(text);
  }

  public void setRevision(final Revision revision) {
    if (revision == Revision.HEAD) {
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
}
