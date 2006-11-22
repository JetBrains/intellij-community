package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.RefactoringFactory;
import com.intellij.refactoring.MoveClassesOrPackagesRefactoring;
import com.intellij.refactoring.move.MoveHandler;
import com.intellij.refactoring.move.MoveCallback;

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
        if (!(object instanceof PsiClass)) {
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
        if (!(object instanceof PsiClass)) {
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
      final PsiPackage aPackage = directory.getPackage();
      if (aPackage != null) {
        final VirtualFile srcRoot = ProjectRootManager.getInstance(myPane.myProject).getFileIndex().getSourceRootForFile(directory.getVirtualFile());
        assert srcRoot != null;
        final RefactoringFactory factory = RefactoringFactory.getInstance(myPane.myProject);
        MoveDestination destination = factory.createSourceRootMoveDestination(aPackage.getQualifiedName(), srcRoot);
        int dropAction = dtde.getDropAction();
        if ((dropAction & DnDConstants.ACTION_MOVE) != 0) {
          MoveHandler.doMove(myPane.myProject, elements, directory, null);
          dtde.dropComplete(true);
          return true;
        }
      }
    }
    return false;
  }
}
