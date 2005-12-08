package com.intellij.ide.todo.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.todo.TodoTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.jetbrains.annotations.NotNull;

public class SingleFileToDoNode extends BaseToDoNode<PsiFile>{
  private final TodoFileNode myFileNode = new TodoFileNode(getProject(), getValue(), myBuilder, true);

  public SingleFileToDoNode(Project project, PsiFile value, TodoTreeBuilder builder) {
    super(project, value, builder);
  }

  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    return new ArrayList<AbstractTreeNode>(Collections.singleton(myFileNode));
  }

  public void update(PresentationData presentation) {
  }

  public Object getFileNode() {
    return myFileNode;
  }
}
