package com.jetbrains.python.psi.impl;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyBuiltinCache {
  public static PyBuiltinCache getInstance(Project project) {
    return ServiceManager.getService(project, PyBuiltinCache.class);
  }

  private Project myProject;
  private PyFile myBuiltinsFile;

  public PyBuiltinCache(final Project project) {
    myProject = project;
  }

  public PyFile getBuiltinsFile() {
    if (myBuiltinsFile == null) {
      final PsiFile[] builtinFiles = FilenameIndex.getFilesByName(myProject, "__builtin__.py", GlobalSearchScope.allScope(myProject));
      if (builtinFiles.length == 1) {
        myBuiltinsFile = (PyFile) builtinFiles [0];
      }
    }
    return myBuiltinsFile;
  }

  @Nullable
  public PyClass getListClass() {
    PyFile builtinsFile = getBuiltinsFile();
    if (builtinsFile != null) {
      for(PsiElement element: builtinsFile.getChildren()) {
        if (element instanceof PyClass && ((PyClass) element).getName().equals("list")) {
          return (PyClass) element;
        }
      }
    }
    return null;
  }
}
