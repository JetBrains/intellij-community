package com.jetbrains.python.psi.impl;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.*;

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
  public PyClass getClass(String name) {
    PyFile builtinsFile = getBuiltinsFile();
    if (builtinsFile != null) {
      for(PsiElement element: builtinsFile.getChildren()) {
        if (element instanceof PyClass && ((PyClass) element).getName().equals(name)) {
          return (PyClass) element;
        }
      }
    }
    return null;
  }

  protected Map<String,PyClassType> myTypeCache = new HashMap<String,PyClassType>();
  
  /**
  @return 
  */
  @NotNull
  protected PyClassType _getObjectType(String name) {
    PyClassType val = myTypeCache.get(name);
    if (val == null) {
      PyClass cls = getClass(name);
      val = new PyObjectType(cls);
      myTypeCache.put(name, val);
    }
    return val;
  }
  
  public PyClassType getObjectType() {
    return _getObjectType("object");
  }
  
  public PyClassType getListType() {
    return _getObjectType("list");
  }
  
}
