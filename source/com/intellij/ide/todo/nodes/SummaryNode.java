package com.intellij.ide.todo.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.todo.CurrentFileTodosTreeBuilder;
import com.intellij.ide.todo.ToDoSummary;
import com.intellij.ide.todo.TodoFileDirAndModuleComparator;
import com.intellij.ide.todo.TodoTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.text.MessageFormat;

public class SummaryNode extends BaseToDoNode<ToDoSummary> {
  public SummaryNode(Project project, ToDoSummary value, TodoTreeBuilder builder) {
    super(project, value, builder);
  }

  public Collection<AbstractTreeNode> getChildren() {
    ArrayList<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();

    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(getProject()).getFileIndex();
    if (myToDoSettings.isModulesShown()) {
      for (Iterator i = myBuilder.getAllFiles(); i.hasNext();) {
        final PsiFile psiFile = (PsiFile)i.next();
        if (psiFile == null) { // skip invalid PSI files
          continue;
        }
        final VirtualFile virtualFile = psiFile.getVirtualFile();
        Module module = projectFileIndex.getModuleForFile(virtualFile);
        if (module != null) {
          ModuleToDoNode moduleToDoNode = new ModuleToDoNode(getProject(), module, myBuilder);
          if (!children.contains(moduleToDoNode)) {
            children.add(moduleToDoNode);
          }
        }
      }
    }
    else {
      if (myToDoSettings.getIsPackagesShown()) {
        if (myBuilder instanceof CurrentFileTodosTreeBuilder){
          final Iterator allFiles = myBuilder.getAllFiles();
          if(allFiles.hasNext()){
            children.add(new TodoFileNode(myProject, (PsiFile)allFiles.next(), myBuilder, false));
          }
        } else {
          TodoPackageUtil.addPackagesToChildren(children, null, myBuilder, getProject());
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
    }
    Collections.sort(children, TodoFileDirAndModuleComparator.ourInstance);
    return children;

  }

  public void update(PresentationData presentation) {
    int todoItemCount = getValue().getTodoItemCount();
    int fileCount = getValue().getFileCount();
    presentation.setPresentableText(IdeBundle.message("node.todo.summary", todoItemCount, fileCount));
  }

  public String getTestPresentation() {
    return "Summary";
  }
}
