package com.intellij.openapi.roots.ui.configuration.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.fileChooser.ex.FileNodeDescriptor;
import com.intellij.openapi.fileChooser.ex.RootFileElement;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 14
 * @author 2003
 *         Time: 3:07:14 PM
 */
public abstract class ContentEntryEditingAction extends ToggleAction implements CustomComponentAction{
  protected final JTree myTree;

  protected ContentEntryEditingAction(JTree tree) {
    myTree = tree;
    getTemplatePresentation().setEnabled(true);
  }

  public void update(AnActionEvent e) {
    super.update(e);
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(true);
    final VirtualFile[] files = getSelectedFiles();
    if (files == null || files.length == 0) {
      presentation.setEnabled(false);
      return;
    }
    for (VirtualFile file : files) {
      if (!file.isDirectory()) {
        presentation.setEnabled(false);
        break;
      }
    }
  }

  @Nullable
  protected final VirtualFile[] getSelectedFiles() {
    final TreePath[] selectionPaths = myTree.getSelectionPaths();
    if (selectionPaths == null) {
      return null;
    }
    final VirtualFile[] selected = new VirtualFile[selectionPaths.length];
    for (int i = 0; i < selectionPaths.length; i++) {
      TreePath treePath = selectionPaths[i];
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)treePath.getLastPathComponent();
      final Object nodeDescriptor = node.getUserObject();
      if (!(nodeDescriptor instanceof FileNodeDescriptor)) {
        return null;
      }
      selected[i] = ((FileNodeDescriptor)nodeDescriptor).getElement().getFile();
    }
    return selected;
  }

  public JComponent createCustomComponent(Presentation presentation) {
    return new ActionButtonWithText(this, presentation, ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
  }
}
