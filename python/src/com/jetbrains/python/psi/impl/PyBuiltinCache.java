package com.jetbrains.python.psi.impl;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.types.PyClassType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
public class PyBuiltinCache {

  public static final @NonNls String BUILTIN_FILE = "__builtin__.py"; 

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
      final PsiFile[] builtinFiles = FilenameIndex.getFilesByName(myProject, BUILTIN_FILE, GlobalSearchScope.allScope(myProject));
      if (builtinFiles.length == 1) {
        myBuiltinsFile = (PyFile) builtinFiles [0];
      }
    }
    return myBuiltinsFile;
  }

  @Nullable
  public PyClass getClass(@NonNls String name) {
    PyFile builtinsFile = getBuiltinsFile();
    if (builtinsFile != null) {
      for(PsiElement element: builtinsFile.getChildren()) {
        if (element instanceof PyClass && (name != null) && name.equals(((PyClass) element).getName())) {
          return (PyClass) element;
        }
      }
    }
    return null;
  }

  protected Map<String,PyClassType> myTypeCache = new HashMap<String, PyClassType>();
  
  /**
  @return 
  */
  @Nullable
  protected PyClassType _getObjectType(@NonNls String name) {
    PyClassType val = myTypeCache.get(name);
    if (val == null) {
      PyClass cls = getClass(name);
      if (cls != null) { // null may happen during testing
        val = new PyClassType(cls, false);
        myTypeCache.put(name, val);
      }
    }
    return val;
  }
  
  @Nullable
  public PyClassType getObjectType() {
    return _getObjectType("object");
  }
  
  @Nullable
  public PyClassType getListType() {
    return _getObjectType("list");
  }
  
  @Nullable
  public PyClassType getOldstyleClassobjType() {
    return _getObjectType("___Classobj");
  }

}
