/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: Alexander.Kitaev
 * Date: 19.07.2006
 * Time: 16:51:09
 */
package org.jetbrains.idea.svn.dialogs.browser;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.RepositoryBrowserComponent;
import org.jetbrains.idea.svn.dialogs.RepositoryTreeNode;
import org.tmatesoft.svn.core.SVNURL;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

public class DiffOptionsDialog extends DialogWrapper implements ActionListener {

  private final SVNURL myURL;
  private final Project myProject;
  private RepositoryBrowserComponent myBrowser;
  private final SVNURL myRootURL;

  private JRadioButton myUnifiedDiffButton;
  private JRadioButton myUIDiffButton;
  private TextFieldWithBrowseButton myFileBrowser;
  private JCheckBox myReverseDiffButton;
  private JLabel mySourceUrlLabel;
  private JPanel myMainPanel;
  private JLabel myErrorLabel;
  @NonNls private static final String DEFAULT_PATCH_NAME = "diff.txt";

  public DiffOptionsDialog(Project project, SVNURL rootURL, SVNURL url) {
    super(project, true);
    myURL = url;
    myRootURL = rootURL;
    myProject = project;
    setTitle(SvnBundle.message("diff.options.title"));
    mySourceUrlLabel.setText(myURL.toString());
    myBrowser.setRepositoryURL(myRootURL, false);
    myBrowser.addChangeListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        update();
      }
    });
    myUIDiffButton.addActionListener(this);
    myUnifiedDiffButton.addActionListener(this);
    init();
    myFileBrowser.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        File f = selectFile("Patch File", "Select file to store unified diff");
        if (f != null) {
          if (f.exists() && f.isDirectory()) {
            f = new File(f, DEFAULT_PATCH_NAME);
          }
          myFileBrowser.setText(f.getAbsolutePath());
        }
      }
    });
    myFileBrowser.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        update();
      }
    });
    final VirtualFile baseDir = myProject.getBaseDir();
    if (baseDir != null) {
      String projectDir = baseDir.getPath();
      projectDir = projectDir.replace('/', File.separatorChar);
      myFileBrowser.setText(projectDir + File.separatorChar + DEFAULT_PATCH_NAME);
    }
  }

  @NonNls
  protected String getDimensionServiceKey() {
    return "svn4idea.diff.options";
  }

  @Override
  protected void dispose() {
    super.dispose();
    Disposer.dispose(myBrowser);
  }

  public SVNURL getSourceURL() {
    return myURL;
  }

  public boolean isReverseDiff() {
    return myReverseDiffButton.isSelected();
  }

  public SVNURL getTargetURL() {
    if (getOKAction().isEnabled() && myBrowser.getSelectedNode() != null) {
        return myBrowser.getSelectedNode().getURL();
    }
    return null;
  }

  public File getTargetFile() {
    return new File(myFileBrowser.getText());
  }

  public boolean isUnifiedDiff() {
    return myUnifiedDiffButton.isSelected();
  }

  protected JComponent createCenterPanel() {
    update();
    return myMainPanel;
  }

  private void update() {
    RepositoryTreeNode baseNode = myBrowser.getSelectedNode();
    if (baseNode == null) {
      myErrorLabel.setText(SvnBundle.message("diff.options.no.url.error"));
      getOKAction().setEnabled(false);
      return;
    }
    if (myURL.equals(getTargetURL())) {
      myErrorLabel.setText(SvnBundle.message("diff.options.same.url.error"));
      getOKAction().setEnabled(false);
      return;
    }
    if (myUnifiedDiffButton.isSelected() && (myFileBrowser.getText().length() == 0 || getTargetFile().getParentFile() == null)) {
      myErrorLabel.setText(SvnBundle.message("diff.options.no.patch.file.error"));
      getOKAction().setEnabled(false);
      return;
    }
    myErrorLabel.setText(" ");
    getOKAction().setEnabled(true);
  }

  public JComponent getPreferredFocusedComponent() {
    return myBrowser;
  }

  @Nullable
  private File selectFile(String title, String description) {
    FileChooserDescriptor fcd = FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor();
    fcd.setShowFileSystemRoots(true);
    fcd.setTitle(title);
    fcd.setDescription(description);
    fcd.setHideIgnored(false);
    VirtualFile file = FileChooser.chooseFile(fcd, myBrowser, myProject, null);
    if (file == null) {
      return null;
    }
    return new File(file.getPath());
  }

  public void actionPerformed(ActionEvent e) {
    myFileBrowser.setEnabled(myUnifiedDiffButton.isSelected());
    update();
  }

  private void createUIComponents() {
    myBrowser = new RepositoryBrowserComponent(SvnVcs.getInstance(myProject));
  }
}
