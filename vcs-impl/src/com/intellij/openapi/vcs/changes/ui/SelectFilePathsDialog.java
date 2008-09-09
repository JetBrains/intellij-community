/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class SelectFilePathsDialog extends AbstractSelectFilesDialog<FilePath> {

  public SelectFilePathsDialog(final Project project, List<FilePath> originalFiles, final String prompt,
                           final VcsShowConfirmationOption confirmationOption) {
    super(project, false, confirmationOption, prompt);
    myFileList = new ChangesTreeList<FilePath>(project, originalFiles, true, true) {
      protected DefaultTreeModel buildTreeModel(final List<FilePath> changes) {
        return new TreeModelBuilder(project, false).buildModelFromFilePaths(changes);
      }

      protected List<FilePath> getSelectedObjects(final ChangesBrowserNode node) {
        return node.getAllFilePathsUnder();
      }

      @Nullable
      protected FilePath getLeadSelectedObject(final ChangesBrowserNode node) {
        final Object userObject = node.getUserObject();
        if (userObject instanceof FilePath) {
          return (FilePath) userObject;
        }
        return null;
      }
    };
    myFileList.setChangesToDisplay(originalFiles);
    myPanel.add(myFileList, BorderLayout.CENTER);
    init();
  }

  public Collection<FilePath> getSelectedFiles() {
    return myFileList.getIncludedChanges();
  }
}