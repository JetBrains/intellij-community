package com.intellij.ide.todo.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.todo.ToDoSummary;
import com.intellij.ide.todo.TodoFileDirComparator;
import com.intellij.ide.todo.TodoTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

public class SummaryNode extends BaseToDoNode<ToDoSummary> {

  public SummaryNode(Project project, ToDoSummary value, TodoTreeBuilder builder) {
    super(project, value, builder);
  }

  public Collection<AbstractTreeNode> getChildren() {
    ArrayList<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();

    if (myToDoSettings.getIsPackagesShown()) {
      for (Iterator i = myBuilder.getAllFiles(); i.hasNext();) {
        final PsiFile psiFile = (PsiFile)i.next();
        if (psiFile == null) { // skip invalid PSI files
          continue;
        }
        final VirtualFile virtualFile = psiFile.getVirtualFile();
        final VirtualFile contentRoot = ProjectRootManager.getInstance(getProject()).getFileIndex().getContentRootForFile(virtualFile);
        if (contentRoot != null) {
          final PsiDirectory projectRoot = PsiManager.getInstance(getProject()).findDirectory(contentRoot);
          TodoDirNode projectRootNode = new TodoDirNode(getProject(), projectRoot, myBuilder);
          if (projectRoot != null && !children.contains(projectRootNode)) {
            children.add(projectRootNode);
          }
        }
      }
    }
    else {
      for (Iterator i = myBuilder.getAllFiles(); i.hasNext();) {
        final PsiFile psiFile = (PsiFile)i.next();
        if (psiFile == null) { // skip invalid PSI files
          continue;
        }
        TodoFileNode fileNode = new TodoFileNode(getProject(), psiFile, myBuilder, false);
        if (getTreeStructure().accept(psiFile) && !children.contains(fileNode)) {
          children.add(fileNode);
        }
      }
    }
    Collections.sort(children, TodoFileDirComparator.ourInstance);
    return children;

  }

  public void update(PresentationData presentation) {
    StringBuffer sb = new StringBuffer();
    int todoItemCount = getValue().getTodoItemCount();
    sb.append("Found ").append(todoItemCount).append(" TODO item");
    if (todoItemCount != 1) {
      sb.append('s');
    }
    int fileCount = getValue().getFileCount();
    sb.append(" in ").append(fileCount).append(" file");
    if (fileCount != 1) {
      sb.append('s');
    }
    presentation.setPresentableText(sb.toString());
  }

  public String getTestPresentation() {
    return "Summary";
  }
}
