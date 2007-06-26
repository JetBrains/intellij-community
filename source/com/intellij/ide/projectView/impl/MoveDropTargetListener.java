package com.intellij.ide.projectView.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.refactoring.actions.MoveAction;
import com.intellij.refactoring.copy.CopyHandler;
import com.intellij.refactoring.move.MoveHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author vladk
 */
class MoveDropTargetListener implements DropTargetListener {
  final private DataFlavor dataFlavor;
  final private Project myProject;
  final private JTree myTree;
  final private PsiRetriever myPsiRetriever;

  public interface PsiRetriever {
    @Nullable
    PsiElement getPsiElement( @Nullable TreeNode node );
  }

  public interface ModifierSource {
    int getModifiers();
  }

  public MoveDropTargetListener(final PsiRetriever psiRetriever, final JTree tree, final Project project, final DataFlavor flavor) {
    myPsiRetriever = psiRetriever;
    myProject = project;
    myTree = tree;
    dataFlavor = flavor;
  }

  public void dragEnter(DropTargetDragEvent dtde) {
    final TreeNode[] sourceNodes = getSourceNodes(dtde.getTransferable());
    if (sourceNodes != null && getDropHandler(dtde.getDropAction()).isValidSource(sourceNodes)) {
      dtde.acceptDrag(dtde.getDropAction());
    }
    else {
      dtde.rejectDrag();
    }
  }

  public void dragOver(DropTargetDragEvent dtde) {
    final TreeNode[] sourceNodes = getSourceNodes(dtde.getTransferable());
    final TreeNode targetNode = getTargetNode(dtde.getLocation());
    final int dropAction = dtde.getDropAction();
    if (sourceNodes != null && targetNode != null && canDrop(sourceNodes, targetNode, dropAction)) {
      dtde.acceptDrag(dropAction);
    }
    else {
      dtde.rejectDrag();
    }
  }

  public void dropActionChanged(DropTargetDragEvent dtde) {
  }

  public void dragExit(DropTargetEvent dte) {
  }

  public void drop(DropTargetDropEvent dtde) {
    final TreeNode[] sourceNodes = getSourceNodes(dtde.getTransferable());
    final TreeNode targetNode = getTargetNode(dtde.getLocation());
    final int dropAction = dtde.getDropAction();
    if ((dropAction & DnDConstants.ACTION_COPY_OR_MOVE) == 0 || sourceNodes == null || targetNode == null ||
        !doDrop(sourceNodes, targetNode, dropAction, dtde)) {
      dtde.rejectDrop();
    }
  }

  @Nullable
  private TreeNode[] getSourceNodes(final Transferable transferable) {
    try {
      Object transferData = transferable.getTransferData(dataFlavor);
      if (transferData instanceof AbstractProjectViewPSIPane.TransferableWrapper) {
        return ((AbstractProjectViewPSIPane.TransferableWrapper)transferData).getTreeNodes();
      }
      return null;
    }
    catch (UnsupportedFlavorException e) {
      return null;
    }
    catch (IOException e) {
      return null;
    }
  }

  @Nullable
  private TreeNode getTargetNode(final Point location) {
    final TreePath path = myTree.getPathForLocation(location.x, location.y);
    return path == null ? null : (TreeNode)path.getLastPathComponent();
  }

  private boolean canDrop(@NotNull final TreeNode[] sourceNodes, @NotNull final TreeNode targetNode, final int dropAction) {
    return doDrop(sourceNodes, targetNode, dropAction, null);
  }

  private boolean doDrop(@NotNull final TreeNode[] sourceNodes,
                         @NotNull final TreeNode targetNode,
                         final int dropAction,
                         @Nullable final DropTargetDropEvent dtde) {
    TreeNode validTargetNode = getValidTargetNode(sourceNodes, targetNode, dropAction);
    if (validTargetNode != null) {
      final TreeNode[] filteredSourceNodes = removeRedundantSourceNodes(sourceNodes, validTargetNode, dropAction);
      if (filteredSourceNodes.length != 0) {
        if (dtde != null) {
          dtde.dropComplete(true);
          getDropHandler(dropAction).doDrop(filteredSourceNodes, validTargetNode);
        }
        return true;
      }
    }
    return false;
  }

  @Nullable
  private TreeNode getValidTargetNode(final @NotNull TreeNode[] sourceNodes, final @NotNull TreeNode targetNode, final int dropAction) {
    final DropHandler dropHandler = getDropHandler(dropAction);
    TreeNode currentNode = targetNode;
    while (true) {
      if (dropHandler.isValidTarget(sourceNodes, currentNode)) {
        return currentNode;
      }
      if (!dropHandler.shouldDelegateToParent(currentNode)) return null;
      currentNode = currentNode.getParent();
      if (currentNode == null) return null;
    }
  }

  private TreeNode[] removeRedundantSourceNodes(@NotNull final TreeNode[] sourceNodes, @NotNull final TreeNode targetNode, final int dropAction) {
    final DropHandler dropHandler = getDropHandler(dropAction);
    List<TreeNode> result = new ArrayList<TreeNode>(sourceNodes.length);
    for (TreeNode sourceNode : sourceNodes) {
      if (!dropHandler.isDropRedundant(sourceNode, targetNode)) {
        result.add(sourceNode);
      }
    }
    return result.toArray(new TreeNode[result.size()]);
  }

  public DropHandler getDropHandler(final int dropAction) {
    return (dropAction == DnDConstants.ACTION_COPY ) ? new CopyDropHandler() : new MoveDropHandler();
  }

  private interface DropHandler {
    boolean isValidSource(@NotNull TreeNode[] sourceNodes);

    boolean isValidTarget(@NotNull TreeNode[] sourceNodes, @NotNull TreeNode targetNode);

    boolean shouldDelegateToParent(@NotNull TreeNode targetNode);

    boolean isDropRedundant(@NotNull TreeNode sourceNode, @NotNull TreeNode targetNode);

    void doDrop(@NotNull TreeNode[] sourceNodes, @NotNull TreeNode targetNode);
  }

  public abstract class MoveCopyDropHandler implements DropHandler {

    public boolean isValidSource(@NotNull final TreeNode[] sourceNodes) {
      return canDrop(sourceNodes, null);
    }

    public boolean isValidTarget(@NotNull final TreeNode[] sourceNodes, final @NotNull TreeNode targetNode) {
      return canDrop(sourceNodes, targetNode);
    }

    public boolean shouldDelegateToParent(@NotNull final TreeNode targetNode) {
      final PsiElement psiElement = getPsiElement(targetNode);
      return psiElement == null || (!(psiElement instanceof PsiPackage) && !(psiElement instanceof PsiDirectory));
    }

    protected abstract boolean canDrop(@NotNull TreeNode[] sourceNodes, @Nullable TreeNode targetNode);

    @Nullable
    protected PsiElement getPsiElement(@Nullable final TreeNode treeNode) {
      return myPsiRetriever.getPsiElement(treeNode);
    }

    @NotNull protected PsiElement[] getPsiElements(@NotNull TreeNode[] nodes) {
      List<PsiElement> psiElements = new ArrayList<PsiElement>(nodes.length);
      for (TreeNode node : nodes) {
        PsiElement psiElement = getPsiElement(node);
        if (psiElement != null) {
          psiElements.add(psiElement);
        }
      }
      if ( psiElements.size() != 0) {
        return psiElements.toArray(new PsiElement[psiElements.size()]);
      } else {
        return BaseRefactoringAction.getPsiElementArray(DataManager.getInstance().getDataContext(myTree));
      }
    }

    protected boolean fromSameProject(final PsiElement[] sourceElements, final PsiElement targetElement) {
      return targetElement != null && sourceElements.length > 0 && sourceElements[0] != null &&
             targetElement.getProject() == sourceElements[0].getProject();
    }
  }

  private class MoveDropHandler extends MoveCopyDropHandler {

    protected boolean canDrop(@NotNull final TreeNode[] sourceNodes, @Nullable final TreeNode targetNode) {
      final PsiElement[] sourceElements = getPsiElements(sourceNodes);
      final PsiElement targetElement = getPsiElement(targetNode);
      return sourceElements.length == 0 ||
             ((targetNode == null || targetElement != null) &&
              fromSameProject(sourceElements, targetElement) && MoveHandler.canMove(sourceElements, targetElement));
    }

    public void doDrop(@NotNull final TreeNode[] sourceNodes, @NotNull final TreeNode targetNode) {
      final PsiElement[] sourceElements = getPsiElements(sourceNodes);
      final PsiElement targetElement = getPsiElement(targetNode);
      if (targetElement == null) return;
      final DataContext dataContext = DataManager.getInstance().getDataContext(myTree);
      getActionHandler(dataContext).invoke(myProject, sourceElements, new DataContext() {
        @Nullable
        public Object getData(@NonNls String dataId) {
          if (dataId.equals(DataConstantsEx.TARGET_PSI_ELEMENT)) {
            return targetElement;
          }
          else {
            return dataContext.getData(dataId);
          }
        }
      });
    }

    private RefactoringActionHandler getActionHandler(final DataContext dataContext) {
      final MoveAction.MoveProvider moveProvider = (MoveAction.MoveProvider)dataContext.getData(MoveAction.MOVE_PROVIDER);
      if (moveProvider != null) {
        return moveProvider.getHandler(dataContext);
      }
      else {
        return RefactoringActionHandlerFactory.getInstance().createMoveHandler();
      }
    }

    public boolean isDropRedundant(@NotNull TreeNode sourceNode, @NotNull TreeNode targetNode) {
      return sourceNode.getParent() == targetNode;
    }
  }

  private class CopyDropHandler extends MoveCopyDropHandler {

    protected boolean canDrop(@NotNull final TreeNode[] sourceNodes, @Nullable final TreeNode targetNode) {
      final PsiElement[] sourceElements = getPsiElements(sourceNodes);
      final PsiElement targetElement = getPsiElement(targetNode);
      return ( targetElement instanceof PsiPackage || targetElement instanceof PsiDirectory ) &&
             fromSameProject(sourceElements, targetElement) && CopyHandler.canCopy(sourceElements);
    }

    public void doDrop(@NotNull final TreeNode[] sourceNodes, @NotNull final TreeNode targetNode) {
      final PsiElement[] sourceElements = getPsiElements(sourceNodes);
      final PsiElement targetElement = getPsiElement(targetNode);
      final PsiPackage psiPackage;
      final PsiDirectory psiDirectory;
      if ( targetElement instanceof PsiPackage) {
        psiPackage = (PsiPackage)targetElement;
        final PsiDirectory[] psiDirectories = psiPackage.getDirectories();
        psiDirectory = psiDirectories.length != 0 ? psiDirectories[0] : null;
      } else {
        psiDirectory = (PsiDirectory)targetElement;
        psiPackage = psiDirectory == null ? null : psiDirectory.getPackage();
      }
      CopyHandler.doCopy(sourceElements, psiPackage, psiDirectory );
    }

    public boolean isDropRedundant(@NotNull TreeNode sourceNode, @NotNull TreeNode targetNode) {
      return false;
    }
  }
}
