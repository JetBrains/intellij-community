package com.intellij.openapi.roots.ui.configuration.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.fileChooser.ex.FileNodeDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.ActionToolbarEx;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

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
    final VirtualFile file = getSelectedFile();
    if (file == null) {
      presentation.setEnabled(false);
      return;
    }
    if (!file.isDirectory()) {
      presentation.setEnabled(false);
    }
  }

  protected final VirtualFile getSelectedFile() {
    final TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath == null) {
      return null;
    }
    final DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
    final Object nodeDescriptor = node.getUserObject();
    if (!(nodeDescriptor instanceof FileNodeDescriptor)) {
      return null;
    }
    final Object element = ((FileNodeDescriptor)nodeDescriptor).getElement();
    if (!(element instanceof FileElement)) {
      return null;
    }
    return ((FileElement)element).getFile();
  }

  public JComponent createCustomComponent(Presentation presentation) {
    return new ActionButtonWithText(this, presentation, ActionPlaces.UNKNOWN, ActionToolbarEx.DEFAULT_MINIMUM_BUTTON_SIZE);
  }
}
