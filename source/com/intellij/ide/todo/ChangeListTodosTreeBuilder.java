/**
 * @author Vladimir Kondratyev
 */
package com.intellij.ide.todo;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;

public class ChangeListTodosTreeBuilder extends TodoTreeBuilder{
  public ChangeListTodosTreeBuilder(JTree tree,DefaultTreeModel treeModel,Project project){
    super(tree,treeModel,project);
  }

  protected TodoTreeStructure createTreeStructure(){
    return new ChangeListTodosTreeStructure(myProject);
  }

  void rebuildCache(){
    myFileTree.clear();
    myDirtyFileSet.clear();
    myFile2Highlighter.clear();

    TodoTreeStructure treeStructure=getTodoTreeStructure();
    PsiFile[] psiFiles= mySearchHelper.findFilesWithTodoItems();
    for(int i=0;i<psiFiles.length;i++){
      PsiFile psiFile=psiFiles[i];
      if(mySearchHelper.getTodoItemsCount(psiFile) > 0 && treeStructure.accept(psiFile)){
        myFileTree.add(psiFile.getVirtualFile());
      }
    }

    treeStructure.validateCache();
  }
}