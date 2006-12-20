/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 20.12.2006
 * Time: 16:04:55
 */
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.changes.IgnoredFileBean;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.io.File;

public class IgnoreUnversionedDialog extends DialogWrapper {
  private JRadioButton myIgnoreSpecifiedFileRadioButton;
  private JRadioButton myIgnoreAllFilesUnderRadioButton;
  private JTextField myIgnoreDirectoryTextField;
  private JRadioButton myIgnoreAllFilesMatchingRadioButton;
  private JTextField myIgnoreMaskTextField;
  private JPanel myPanel;
  private List<VirtualFile> myFilesToIgnore;
  private Project myProject;

  public IgnoreUnversionedDialog(final Project project) {
    super(project, false);
    myProject = project;
    setTitle("Ignore Unversioned Files");
    init();
    myIgnoreAllFilesUnderRadioButton.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        myIgnoreDirectoryTextField.setEnabled(myIgnoreAllFilesUnderRadioButton.isSelected());
      }
    });
    myIgnoreAllFilesMatchingRadioButton.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        myIgnoreMaskTextField.setEnabled(myIgnoreAllFilesMatchingRadioButton.isSelected());
      }
    });
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public void setFilesToIgnore(List<VirtualFile> virtualFiles) {
    assert virtualFiles.size() > 0;
    myFilesToIgnore = virtualFiles;
    if (virtualFiles.size() == 1) {
      myIgnoreSpecifiedFileRadioButton.setText("Ignore " + virtualFiles.get(0).getPresentableUrl());
    }
    else {
      myIgnoreSpecifiedFileRadioButton.setText("Ignore specified " + virtualFiles.size() + " files");
    }

    final VirtualFile[] ancestors = VfsUtil.getCommonAncestors(virtualFiles.toArray(new VirtualFile[virtualFiles.size()]));
    if (ancestors.length > 0) {
      myIgnoreDirectoryTextField.setText(ancestors [0].getPresentableUrl());
    }
    else {
      myIgnoreDirectoryTextField.setText(virtualFiles.get(0).getParent().getPresentableUrl());
    }

    final Set<String> extensions = new HashSet<String>();
    for(VirtualFile vf: virtualFiles) {
      final String extension = vf.getExtension();
      if (extension != null) {
        extensions.add(extension);
      }
    }
    if (extensions.size() > 0) {
      final String[] extensionArray = extensions.toArray(new String[extensions.size()]);
      myIgnoreMaskTextField.setText("*." + extensionArray [0]);
    }
    else {
      myIgnoreMaskTextField.setText(virtualFiles.get(0).getPresentableName());
    }
  }

  public IgnoredFileBean[] getSelectedIgnoredFiles() {
    VirtualFile projectDir = myProject.getProjectFile().getParent();
    if (myIgnoreSpecifiedFileRadioButton.isSelected()) {
      IgnoredFileBean[] result = new IgnoredFileBean[myFilesToIgnore.size()];
      for(int i=0; i<myFilesToIgnore.size(); i++) {
        result [i] = new IgnoredFileBean();
        result [i].setPath(VfsUtil.getRelativePath(myFilesToIgnore.get(i), projectDir, '/'));
      }
      return result;
    }
    if (myIgnoreAllFilesUnderRadioButton.isSelected()) {
      IgnoredFileBean result = new IgnoredFileBean();
      String path = FileUtil.getRelativePath(new File(projectDir.getPresentableUrl()), new File(myIgnoreDirectoryTextField.getText()));
      if (path == null) {
        result.setPath(myIgnoreDirectoryTextField.getText().replace(File.separatorChar, '/'));
      }
      else {
        result.setPath(path.replace(File.separatorChar, '/'));
      }
      return new IgnoredFileBean[] { result };
    }
    if (myIgnoreAllFilesMatchingRadioButton.isSelected()) {
      IgnoredFileBean result = new IgnoredFileBean();
      result.setMask(myIgnoreMaskTextField.getText());
      return new IgnoredFileBean[] { result };
    }
    return new IgnoredFileBean[0];
  }
}