// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner;

import com.intellij.ProjectTopics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.jar.JarFileSystemImpl;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class LoaderFactory implements Disposable {
  private final Project myProject;

  private final ConcurrentMap<Module, ClassLoader> myModule2ClassLoader;
  private ClassLoader myProjectClassLoader = null;
  private final MessageBusConnection myConnection;

  public static LoaderFactory getInstance(final Project project) {
    return project.getService(LoaderFactory.class);
  }

  public LoaderFactory(final Project project) {
    myProject = project;
    myModule2ClassLoader = CollectionFactory.createConcurrentWeakMap();
    myConnection = myProject.getMessageBus().connect();
    myConnection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull final ModuleRootEvent event) {
        clearClassLoaderCache();
      }
    });
  }

  @Override
  public void dispose() {
    myConnection.disconnect();
    myModule2ClassLoader.clear();
  }

  @NotNull public ClassLoader getLoader(@NotNull VirtualFile formFile) {
    final Module module = ModuleUtilCore.findModuleForFile(formFile, myProject);
    if (module == null) {
      return getClass().getClassLoader();
    }

    return getLoader(module);
  }

  public ClassLoader getLoader(@NotNull Module module) {
    final ClassLoader cachedLoader = myModule2ClassLoader.get(module);
    if (cachedLoader != null) {
      return cachedLoader;
    }

    final String runClasspath = OrderEnumerator.orderEntries(module).recursively().getPathsList().getPathsString();

    final ClassLoader classLoader = createClassLoader(runClasspath, module.getName());

    myModule2ClassLoader.put(module, classLoader);

    return classLoader;
  }

  @NotNull public ClassLoader getProjectClassLoader() {
    if (myProjectClassLoader == null) {
      final String runClasspath = OrderEnumerator.orderEntries(myProject).withoutSdk().getPathsList().getPathsString();
      myProjectClassLoader = createClassLoader(runClasspath, "<project>");
    }
    return myProjectClassLoader;
  }

  private static ClassLoader createClassLoader(final String runClasspath, final String moduleName) {
    List<Path> files = new ArrayList<>();
    final VirtualFileManager manager = VirtualFileManager.getInstance();
    final JarFileSystemImpl fileSystem = (JarFileSystemImpl)JarFileSystem.getInstance();
    final StringTokenizer tokenizer = new StringTokenizer(runClasspath, File.pathSeparator);
    while (tokenizer.hasMoreTokens()) {
      final String s = tokenizer.nextToken();
      try {
        VirtualFile vFile = manager.findFileByUrl(VfsUtilCore.pathToUrl(s));
        File realFile = fileSystem.getMirroredFile(vFile);
        files.add(realFile == null ? new File(s).toPath() : realFile.toPath());
      }
      catch (Exception e) {
        // ignore ?
      }
    }

    files.add(PathManager.getJarForClass(Spacer.class));
    return new DesignTimeClassLoader(files, LoaderFactory.class.getClassLoader(), moduleName);
  }

  public void clearClassLoaderCache() {
    // clear classes with invalid classloader from UIManager cache
    final UIDefaults uiDefaults = UIManager.getDefaults();
    for (Iterator it = uiDefaults.keySet().iterator(); it.hasNext();) {
      Object key = it.next();
      Object value = uiDefaults.get(key);
      if (value instanceof Class) {
        ClassLoader loader = ((Class<?>)value).getClassLoader();
        if (loader instanceof DesignTimeClassLoader) {
          it.remove();
        }
      }
    }
    myModule2ClassLoader.clear();
    myProjectClassLoader = null;
  }

  private static final class DesignTimeClassLoader extends UrlClassLoader {
    private static final boolean isParallelCapable = registerAsParallelCapable();

    private final String myModuleName;

    DesignTimeClassLoader(List<Path> files, ClassLoader parent, String moduleName) {
      super(UrlClassLoader.build().files(files).allowLock(false).parent(parent), isParallelCapable);

      myModuleName = moduleName;
    }

    @Override
    public String toString() {
      return "DesignTimeClassLoader:" + myModuleName;
    }
  }
}