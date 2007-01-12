package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesToNewDirectoryDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.io.IOException;

/**
 * @author ven
*/
class MoveDropTargetListener implements DropTargetListener {
  private AbstractProjectViewPSIPane myPane;

  public MoveDropTargetListener(final AbstractProjectViewPSIPane pane) {
    myPane = pane;
  }

  public void dragEnter(DropTargetDragEvent dtde) {
    try {
      Object[] objects = (Object[])dtde.getTransferable().getTransferData(AbstractProjectViewPSIPane.FLAVORS[0]);
      for (Object object : objects) {
        if (!isValidDropTarget(object)) {
          dtde.rejectDrag();
          break;
        }
      }
    }
    catch (UnsupportedFlavorException e) {
      dtde.rejectDrag();
    }
    catch (IOException e) {
      dtde.rejectDrag();
    }
  }

  private boolean isValidDropTarget(final Object object) {
    return object instanceof PsiClass;
  }

  public void dragOver(DropTargetDragEvent dtde) {}

  public void dropActionChanged(DropTargetDragEvent dtde) {}

  public void dragExit(DropTargetEvent dte) {}

  public void drop(DropTargetDropEvent dtde) {
    PsiElement[] elements;
    try {
      Object[] objects = (Object[])dtde.getTransferable().getTransferData(AbstractProjectViewPSIPane.FLAVORS[0]);
      elements = new PsiElement[objects.length];
      for (int i = 0; i < objects.length; i++) {
        Object object = objects[i];
        if (!isValidDropTarget(object)) {
          elements = null;
          break;
        }
        elements[i] = (PsiElement)object;
      }
    }
    catch (UnsupportedFlavorException e) {
      elements = null;
    }
    catch (IOException e) {
      elements = null;
    }

    if (elements != null) {
      final Point location = dtde.getLocation();
      final TreePath path = myPane.myTree.getPathForLocation(location.x, location.y);
      if (path != null) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
        if (doMove(node, dtde, elements)) return;
        node = (DefaultMutableTreeNode)node.getParent();
        if (node != null && doMove(node, dtde, elements)) return;
      }
    }
    dtde.rejectDrop();
  }

  private boolean doMove(final DefaultMutableTreeNode node, final DropTargetDropEvent dtde, final PsiElement[] elements) {
    final Object userObject = node.getUserObject();
    if (userObject instanceof PsiDirectoryNode) {
      final PsiDirectoryNode directoryNode = (PsiDirectoryNode)userObject;
      final PsiDirectory directory = directoryNode.getValue();
      if (isTheSameDirectory(directory, elements)) return true;
      final VirtualFile srcRoot = ProjectRootManager.getInstance(myPane.myProject).getFileIndex().getSourceRootForFile(directory.getVirtualFile());
      assert srcRoot != null;
      int dropAction = dtde.getDropAction();
      if ((dropAction & DnDConstants.ACTION_MOVE) != 0) {
        dtde.dropComplete(true);
        if (!CommonRefactoringUtil.checkReadOnlyStatus(myPane.myProject, elements)) return true;
        MoveClassesToNewDirectoryDialog dialog = new MoveClassesToNewDirectoryDialog(directory, elements);
        dialog.show();
        return true;
      }
    }
    return false;
  }

  private boolean isTheSameDirectory(final PsiDirectory directory, final PsiElement[] elements) {
    for (final PsiElement element : elements) {
      final PsiFile aFile = element.getContainingFile();
      assert aFile instanceof PsiJavaFile;
      if (!directory.equals(aFile.getContainingDirectory())) return false;
    }
    return true;
  }
}
