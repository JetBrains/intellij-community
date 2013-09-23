package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.impl.PyQualifiedName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public abstract class PythonPathCache {
  private final Map<PyQualifiedName, List<PsiElement>> myCache = new HashMap<PyQualifiedName, List<PsiElement>>();
  private final Map<VirtualFile, List<PyQualifiedName>> myQNameCache = new HashMap<VirtualFile, List<PyQualifiedName>>();

  protected void clearCache() {
    myCache.clear();
    myQNameCache.clear();
  }

  public synchronized List<PsiElement> get(PyQualifiedName qualifiedName) {
    return myCache.get(qualifiedName);
  }

  public synchronized void put(PyQualifiedName qualifiedName, List<PsiElement> results) {
    myCache.put(qualifiedName, results);
  }

  public synchronized List<PyQualifiedName> getNames(VirtualFile vFile) {
    return myQNameCache.get(vFile);
  }
  
  public synchronized void putNames(VirtualFile vFile, List<PyQualifiedName> qNames) {
    myQNameCache.put(vFile, qNames);
  }

  protected class MyVirtualFileAdapter extends VirtualFileAdapter {
    @Override
    public void fileCreated(VirtualFileEvent event) {
      clearCache();
    }

    @Override
    public void fileDeleted(VirtualFileEvent event) {
      clearCache();
    }

    @Override
    public void fileMoved(VirtualFileMoveEvent event) {
      clearCache();
    }

    @Override
    public void fileCopied(VirtualFileCopyEvent event) {
      clearCache();
    }

    @Override
    public void propertyChanged(VirtualFilePropertyEvent event) {
      if (event.getPropertyName().equals(VirtualFile.PROP_NAME)) {
        clearCache();
      }
    }
  }
}
