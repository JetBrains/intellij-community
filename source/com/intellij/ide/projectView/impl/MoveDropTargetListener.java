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
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author vladk
 */
class MoveDropTargetListener implements DropTargetListener {
  final private DataFlavor dataFlavor;
  private final ModifierSource myModifierSource;
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

  public MoveDropTargetListener(final PsiRetriever psiRetriever, final ModifierSource modifierSource, final JTree tree, final Project project, final DataFlavor flavor) {
    myPsiRetriever = psiRetriever;
    myModifierSource = modifierSource;
    myProject = project;
    myTree = tree;
    dataFlavor = flavor;
  }

  public void dragEnter(DropTargetDragEvent dtde) {
    final TreeNode[] sourceNodes = getSourceNodes(dtde.getTransferable());
    if (sourceNodes != null && getDropHandler().isValidSource(sourceNodes)) {
      dtde.acceptDrag(dtde.getDropAction());
    }
    else {
      dtde.rejectDrag();
    }
  }

  public void dragOver(DropTargetDragEvent dtde) {
    final TreeNode[] sourceNodes = getSourceNodes(dtde.getTransferable());
    final TreeNode targetNode = getTargetNode(dtde.getLocation());
    if (sourceNodes != null && targetNode != null && canDrop(sourceNodes, targetNode)) {
      dtde.acceptDrag(dtde.getDropAction());
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
    if ((dtde.getDropAction() & DnDConstants.ACTION_COPY_OR_MOVE) == 0 || sourceNodes == null || targetNode == null ||
        !doDrop(sourceNodes, targetNode, dtde)) {
      dtde.rejectDrop();
    }
  }

  @Nullable
  private TreeNode[] getSourceNodes(final Transferable transferable) {
    try {
      return ((AbstractProjectViewPSIPane.TransferableWrapper)transferable.getTransferData(dataFlavor)).getTreeNodes();
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

  private boolean canDrop(@NotNull final TreeNode[] sourceNodes, @NotNull final TreeNode targetNode) {
    return doDrop(sourceNodes, targetNode, null);
  }

  private boolean doDrop(@NotNull final TreeNode[] sourceNodes,
                         @NotNull final TreeNode targetNode,
                         @Nullable final DropTargetDropEvent dtde) {
    TreeNode validTargetNode = getValidTargetNode(sourceNodes, targetNode);
    if (validTargetNode != null) {
      final TreeNode[] filteredSourceNodes = removeRedundantSourceNodes(sourceNodes, validTargetNode);
      if (filteredSourceNodes.length != 0) {
        if (dtde != null) {
          dtde.dropComplete(true);
          getDropHandler().doDrop(filteredSourceNodes, validTargetNode);
        }
        return true;
      }
    }
    return false;
  }

  @Nullable
  private TreeNode getValidTargetNode(final @NotNull TreeNode[] sourceNodes, final @NotNull TreeNode targetNode) {
    final DropHandler dropHandler = getDropHandler();
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

  private TreeNode[] removeRedundantSourceNodes(@NotNull final TreeNode[] sourceNodes, @NotNull final TreeNode targetNode) {
    final DropHandler dropHandler = getDropHandler();
    List<TreeNode> result = new ArrayList<TreeNode>(sourceNodes.length);
    for (TreeNode sourceNode : sourceNodes) {
      if (!dropHandler.isDropRedundant(sourceNode, targetNode)) {
        result.add(sourceNode);
      }
    }
    return result.toArray(new TreeNode[result.size()]);
  }

  public DropHandler getDropHandler() {
    return (myModifierSource.getModifiers() & KeyEvent.SHIFT_DOWN_MASK ) != 0 ? new CopyDropHandler() : new MoveDropHandler();
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

    public boolean isDropRedundant(@NotNull TreeNode sourceNode, @NotNull TreeNode targetNode) {
      return sourceNode.getParent() == targetNode;
    }

    protected abstract boolean canDrop(@NotNull TreeNode[] sourceNodes, @Nullable TreeNode targetNode);

    @Nullable
    protected PsiElement getPsiElement(@Nullable final TreeNode treeNode) {
      return myPsiRetriever.getPsiElement(treeNode);
    }

    protected PsiElement[] getPsiElements(@NotNull TreeNode[] nodes) {
      List<PsiElement> psiElements = new ArrayList<PsiElement>(nodes.length);
      for (TreeNode node : nodes) {
        PsiElement psiElement = getPsiElement(node);
        if (psiElement != null) {
          psiElements.add(psiElement);
        }
      }
      return psiElements.toArray(new PsiElement[psiElements.size()]);
    }
  }

  private class MoveDropHandler extends MoveCopyDropHandler {

    protected boolean canDrop(@NotNull final TreeNode[] sourceNodes, @Nullable final TreeNode targetNode) {
      final PsiElement[] sourceElements = getPsiElements(sourceNodes);
      final PsiElement targetElement = getPsiElement(targetNode);
      return sourceElements.length == 0 ||
             ((targetNode == null || targetElement != null) && MoveHandler.canMove(sourceElements, targetElement));
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
  }

  private class CopyDropHandler extends MoveCopyDropHandler {

    protected boolean canDrop(@NotNull final TreeNode[] sourceNodes, @Nullable final TreeNode targetNode) {
      final PsiElement[] sourceElements = getPsiElements(sourceNodes);
      final PsiElement targetElement = getPsiElement(targetNode);
      return ( targetElement instanceof PsiPackage || targetElement instanceof PsiDirectory ) && CopyHandler.canCopy(sourceElements);
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
        psiPackage = psiDirectory.getPackage();
      }
      CopyHandler.doCopy(sourceElements, psiPackage, psiDirectory );
    }
  }
}
