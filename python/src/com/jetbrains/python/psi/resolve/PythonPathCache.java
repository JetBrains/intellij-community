package com.jetbrains.python.psi.resolve;

import com.intellij.ProjectTopics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.impl.PyQualifiedName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class PythonPathCache implements Disposable {
  private static final Key<PythonPathCache> KEY = Key.create("PythonPathCache");

  public static PythonPathCache getInstance(Module module) {
    return ModuleServiceManager.getService(module, PythonPathCache.class);
  }

  public static PythonPathCache getInstance(Project project, Sdk sdk) {
    synchronized (KEY) {
      PythonPathCache cache = sdk.getUserData(KEY);
      if (cache == null) {
        cache = new PythonPathCache(project, sdk);
        sdk.putUserData(KEY, cache);
      }
      return cache;
    }
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
    VirtualFileManager.getInstance().addVirtualFileListener(new MyVirtualFileAdapter(), this);
  }

  @Override
  public void dispose() {
  }

  public PythonPathCache(Project project, final Sdk sdk) {
    sdk.getRootProvider().addRootSetChangedListener(new RootProvider.RootSetChangedListener() {
      @Override
      public void rootSetChanged(RootProvider wrapper) {
        myCache.clear();
      }
    }, this);
    VirtualFileManager.getInstance().addVirtualFileListener(new MyVirtualFileAdapter(), this);
    project.getMessageBus().connect(this).subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, new ProjectJdkTable.Listener() {
      @Override
      public void jdkAdded(Sdk jdk) {
      }

      @Override
      public void jdkRemoved(Sdk jdk) {
        if (jdk == sdk) {
          Disposer.dispose(PythonPathCache.this);
        }
      }

      @Override
      public void jdkNameChanged(Sdk jdk, String previousName) {
      }
    });
  }

  public synchronized List<PsiElement> get(PyQualifiedName qualifiedName) {
    return myCache.get(qualifiedName);
  }

  public synchronized void put(PyQualifiedName qualifiedName, List<PsiElement> results) {
    myCache.put(qualifiedName, results);
  }

  private class MyVirtualFileAdapter extends VirtualFileAdapter {
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
  }
}
