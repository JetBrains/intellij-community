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
import com.intellij.util.containers.WeakHashMap;
import com.jetbrains.python.psi.impl.PyQualifiedName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class PythonPathCache implements Disposable {
  private static final Key<Map<Project, PythonPathCache>> KEY = Key.create("PythonPathCache");

  public static PythonPathCache getInstance(Module module) {
    return ModuleServiceManager.getService(module, PythonPathCache.class);
  }

  public static PythonPathCache getInstance(Project project, Sdk sdk) {
    synchronized (KEY) {
      Map<Project, PythonPathCache> cacheMap = sdk.getUserData(KEY);
      if (cacheMap == null) {
        cacheMap = new WeakHashMap<Project, PythonPathCache>();
        sdk.putUserData(KEY, cacheMap);
      }
      PythonPathCache cache = cacheMap.get(project);
      if (cache == null) {
        cache = new PythonPathCache(project, sdk);
        cacheMap.put(project, cache);
      }
      return cache;
    }
  }

  private final Project myProject;
  private final Sdk mySdk;
  private final Map<PyQualifiedName, List<PsiElement>> myCache = new HashMap<PyQualifiedName, List<PsiElement>>();

  @SuppressWarnings({"UnusedDeclaration"})
  public PythonPathCache(Module module) {
    myProject = module.getProject();
    mySdk = null;
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
    if (mySdk != null) {
      synchronized (KEY) {
        final Map<Project, PythonPathCache> cacheMap = mySdk.getUserData(KEY);
        if (cacheMap != null) {
          cacheMap.remove(myProject);
        }
      }
    }
  }

  public PythonPathCache(Project project, final Sdk sdk) {
    myProject = project;
    mySdk = sdk;
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
    Disposer.register(project, this);
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
