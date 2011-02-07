package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.WeakHashMap;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author yole
 */
public class PythonSdkPathCache extends PythonPathCache implements Disposable {
  private static final Key<Map<Project, PythonSdkPathCache>> KEY = Key.create("PythonPathCache");

  public static PythonSdkPathCache getInstance(Project project, Sdk sdk) {
    synchronized (KEY) {
      Map<Project, PythonSdkPathCache> cacheMap = sdk.getUserData(KEY);
      if (cacheMap == null) {
        cacheMap = new WeakHashMap<Project, PythonSdkPathCache>();
        sdk.putUserData(KEY, cacheMap);
      }
      PythonSdkPathCache cache = cacheMap.get(project);
      if (cache == null) {
        cache = new PythonSdkPathCache(project, sdk);
        cacheMap.put(project, cache);
      }
      return cache;
    }
  }

  private final Project myProject;
  private final Sdk mySdk;
  private final AtomicReference<PyBuiltinCache> myBuiltins = new AtomicReference<PyBuiltinCache>();

  public PythonSdkPathCache(final Project project, final Sdk sdk) {
    myProject = project;
    mySdk = sdk;
    sdk.getRootProvider().addRootSetChangedListener(new RootProvider.RootSetChangedListener() {
      @Override
      public void rootSetChanged(RootProvider wrapper) {
        clearCache();
        final Module[] modules = ModuleManager.getInstance(project).getModules();
        for (Module module : modules) {
          PythonModulePathCache.getInstance(module).clearCache();
        }
        myBuiltins.set(null);
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
          Disposer.dispose(PythonSdkPathCache.this);
        }
      }

      @Override
      public void jdkNameChanged(Sdk jdk, String previousName) {
      }
    });
    Disposer.register(project, this);
  }

  @Override
  public void dispose() {
    if (mySdk != null) {
      synchronized (KEY) {
        final Map<Project, PythonSdkPathCache> cacheMap = mySdk.getUserData(KEY);
        if (cacheMap != null) {
          cacheMap.remove(myProject);
        }
      }
    }
  }

  @NotNull
  public PyBuiltinCache getBuiltins() {
    while (true) {
      PyBuiltinCache pyBuiltinCache = myBuiltins.get();
      if (pyBuiltinCache == null || !pyBuiltinCache.isValid()) {
        PyBuiltinCache newCache = new PyBuiltinCache(PyBuiltinCache.getBuiltinsForSdk(myProject, mySdk));
        if (myBuiltins.compareAndSet(pyBuiltinCache, newCache)) {
          return newCache;
        }
      }
      else {
        return pyBuiltinCache;
      }
    }
  }
}
