package com.jetbrains.python.psi.resolve;

import com.intellij.ProjectTopics;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.impl.PyQualifiedName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class PythonPathCache {
  public static PythonPathCache getInstance(Module module) {
    return ModuleServiceManager.getService(module, PythonPathCache.class);
  }

  private final Map<PyQualifiedName, List<PsiElement>> myCache = new HashMap<PyQualifiedName, List<PsiElement>>();

  public PythonPathCache(Module module) {
    module.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        myCache.clear();
      }
    });
    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileAdapter() {
      @Override
      public void fileCreated(VirtualFileEvent event) {
        myCache.clear();
      }

      @Override
      public void fileDeleted(VirtualFileEvent event) {
        myCache.clear();
      }

      @Override
      public void fileMoved(VirtualFileMoveEvent event) {
        myCache.clear();
      }

      @Override
      public void fileCopied(VirtualFileCopyEvent event) {
        myCache.clear();
      }

      @Override
      public void propertyChanged(VirtualFilePropertyEvent event) {
        if (event.getPropertyName().equals(VirtualFile.PROP_NAME)) {
          myCache.clear();
        }
      }
    }, module);
  }

  public synchronized List<PsiElement> get(PyQualifiedName qualifiedName) {
    return myCache.get(qualifiedName);
  }

  public synchronized void put(PyQualifiedName qualifiedName, List<PsiElement> results) {
    myCache.put(qualifiedName, results);
  }
}
