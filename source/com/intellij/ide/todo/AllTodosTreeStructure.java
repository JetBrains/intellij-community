package com.intellij.ide.todo;

import com.intellij.ide.todo.nodes.ToDoRootNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * @author Vladimir Kondratyev
 */
public final class AllTodosTreeStructure extends TodoTreeStructure {
  public AllTodosTreeStructure(final Project project) {
    super(project);
  }

  public boolean accept(final PsiFile psiFile) {
    final boolean
            accept = psiFile.isValid() &&
            (
            myTodoFilter != null && myTodoFilter.accept(mySearchHelper, psiFile) ||
            (myTodoFilter == null && mySearchHelper.getTodoItemsCount(psiFile) > 0)
            );
    return accept;
  }

  public boolean getIsPackagesShown() {
    return myArePackagesShown;
  }

  Object getFirstSelectableElement() {
    return ((ToDoRootNode)myRootElement).getSummaryNode();
  }

  protected AbstractTreeNode createRootElement() {
    return new ToDoRootNode(myProject, new Object(),
                            myBuilder, mySummaryElement);
  }
}