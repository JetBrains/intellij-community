/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class SelectFilesDialog extends AbstractSelectFilesDialog<VirtualFile> {

  public SelectFilesDialog(final Project project, List<VirtualFile> originalFiles, final String prompt,
                           final VcsShowConfirmationOption confirmationOption) {
    super(project, false, confirmationOption, prompt);
    myFileList = new ChangesTreeList<VirtualFile>(project, originalFiles, true, true) {
      protected DefaultTreeModel buildTreeModel(final List<VirtualFile> changes) {
        return new TreeModelBuilder(project, false).buildModelFromFiles(changes);
      }

      protected List<VirtualFile> getSelectedObjects(final ChangesBrowserNode node) {
        return node.getAllFilesUnder();
      }

      protected VirtualFile getLeadSelectedObject(final ChangesBrowserNode node) {
        final Object o = node.getUserObject();
        if (o instanceof VirtualFile) {
          return (VirtualFile) o;
        }
        return null;
      }
    };
    myFileList.setChangesToDisplay(originalFiles);
    myPanel.add(myFileList, BorderLayout.CENTER);
    init();
  }

  public Collection<VirtualFile> getSelectedFiles() {
    return myFileList.getIncludedChanges();
  }
}