package com.intellij.ide.todo.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.todo.ToDoSummary;
import com.intellij.ide.todo.TodoTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public final class ToDoRootNode extends BaseToDoNode{
  private SummaryNode mySummaryNode;

  public ToDoRootNode(Project project, Object value, TodoTreeBuilder builder, ToDoSummary summary) {
    super(project, value, builder);
    mySummaryNode = new SummaryNode(getProject(), summary, myBuilder);
  }

  public boolean contains(VirtualFile file) {
    return false;
  }

  public Collection<AbstractTreeNode> getChildren() {
    return new ArrayList<AbstractTreeNode>(Collections.singleton(mySummaryNode));
  }

  public void update(PresentationData presentation) {
  }

  public Object getSummaryNode() {
    return mySummaryNode;
  }

  public String getTestPresentation() {
    return "Root";
  }
}
