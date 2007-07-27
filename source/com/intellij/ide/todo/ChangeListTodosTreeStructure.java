/*
 * User: anna
 * Date: 27-Jul-2007
 */
package com.intellij.ide.todo;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.PsiFile;
import com.intellij.ide.todo.nodes.ToDoRootNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;

import java.util.Collection;

public class ChangeListTodosTreeStructure extends TodoTreeStructure {
  public ChangeListTodosTreeStructure(Project project) {
    super(project);
  }

  public boolean accept(final PsiFile psiFile) {
    if (!psiFile.isValid()) return false;
    boolean isAffected = false;
    final Collection<Change> changes = ChangeListManager.getInstance(myProject).getDefaultChangeList().getChanges();
    for (Change change : changes) {
      if (change.affectsFile(VfsUtil.virtualToIoFile(psiFile.getVirtualFile()))) {
        isAffected = true;
        break;
      }
    }
    return isAffected && (myTodoFilter != null && myTodoFilter.accept(mySearchHelper, psiFile) ||
                          (myTodoFilter == null && mySearchHelper.getTodoItemsCount(psiFile) > 0));
  }

  public boolean getIsPackagesShown() {
    return myArePackagesShown;
  }

  Object getFirstSelectableElement() {
    return ((ToDoRootNode)myRootElement).getSummaryNode();
  }

  protected AbstractTreeNode createRootElement() {
    return new ToDoRootNode(myProject, new Object(), myBuilder, mySummaryElement);
  }
}