/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class SelectFilesDialog extends DialogWrapper {
  private ChangesTreeList<VirtualFile> myFileList;
  private JPanel myPanel;

  public SelectFilesDialog(final Project project, List<VirtualFile> originalFiles, final String prompt) {
    super(project, false);
    myFileList = new ChangesTreeList<VirtualFile>(project, originalFiles, true, true) {
      protected DefaultTreeModel buildTreeModel(final List<VirtualFile> changes) {
        return new TreeModelBuilder(project, false).buildModelFromFiles(changes);
      }

      protected List<VirtualFile> getSelectedObjects(final ChangesBrowserNode node) {
        return node.getAllFilesUnder();
      }
    };
    myFileList.setChangesToDisplay(originalFiles);
    myPanel = new JPanel(new BorderLayout());
    myPanel.add(myFileList, BorderLayout.CENTER);

    if (prompt != null) {
      final JLabel label = new JLabel(prompt);
      label.setUI(new MultiLineLabelUI());
      myPanel.add(label, BorderLayout.NORTH);
    }

    init();
  }

  @Override
  protected JComponent createNorthPanel() {
    DefaultActionGroup group = new DefaultActionGroup();
    final AnAction[] actions = myFileList.getTreeActions();
    for(AnAction action: actions) {
      group.add(action);
    }
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    return toolbar.getComponent();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myFileList; 
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public Collection<VirtualFile> getSelectedFiles() {
    return myFileList.getIncludedChanges();
  }
}