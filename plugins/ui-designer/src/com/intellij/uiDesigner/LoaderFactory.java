// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentMap;

@Service(Service.Level.PROJECT)
public final class LoaderFactory implements Disposable {
  private final Project myProject;
  private final ConcurrentMap<Module, ClassLoader> myModule2ClassLoader;
  private final MessageBusConnection myConnection;
  private ClassLoader myProjectClassLoader = null;

  public static LoaderFactory getInstance(final Project project) {
    return project.getService(LoaderFactory.class);
  }

  public LoaderFactory(final Project project) {
    myProject = project;
    myModule2ClassLoader = CollectionFactory.createConcurrentWeakMap();
    myConnection = myProject.getMessageBus().connect();
    myConnection.subscribe(ModuleRootListener.TOPIC, new ModuleRootListener() {
      @Override
      public void rootsChanged(final @NotNull ModuleRootEvent event) {
        clearClassLoaderCache();
      }
    });
  }

  @Override
  public void dispose() {
    myConnection.disconnect();
    myModule2ClassLoader.clear();
  }

  public @NotNull ClassLoader getLoader(@NotNull VirtualFile formFile) {
    var module = ModuleUtilCore.findModuleForFile(formFile, myProject);
    return module != null ? getLoader(module) : getClass().getClassLoader();
  }

  public ClassLoader getLoader(@NotNull Module module) {
    var cachedLoader = myModule2ClassLoader.get(module);
    if (cachedLoader != null) return cachedLoader;

    var runClasspath = OrderEnumerator.orderEntries(module).recursively().getPathsList().getPathsString();
    var classLoader = createClassLoader(runClasspath, module.getName());
    myModule2ClassLoader.put(module, classLoader);
    return classLoader;
  }

  public @NotNull ClassLoader getProjectClassLoader() {
    if (myProjectClassLoader == null) {
      var runClasspath = OrderEnumerator.orderEntries(myProject).withoutSdk().getPathsList().getPathsString();
      myProjectClassLoader = createClassLoader(runClasspath, "<project>");
    }
    return myProjectClassLoader;
  }

  private static ClassLoader createClassLoader(String runClasspath, String moduleName) {
    var files = new ArrayList<Path>();
    for (var tokenizer = new StringTokenizer(runClasspath, File.pathSeparator); tokenizer.hasMoreTokens(); ) {
      files.add(Path.of(tokenizer.nextToken()));
    }
    files.add(PathManager.getJarForClass(Spacer.class));
    return new DesignTimeClassLoader(files, LoaderFactory.class.getClassLoader(), moduleName);
  }

  public void clearClassLoaderCache() {
    // clear classes with invalid classloader from UIManager cache
    var uiDefaults = UIManager.getDefaults();
    for (var it = uiDefaults.keySet().iterator(); it.hasNext(); ) {
      var key = it.next();
      var value = uiDefaults.get(key);
      if (value instanceof Class) {
        var loader = ((Class<?>)value).getClassLoader();
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
      super(build().files(files).allowLock(false).parent(parent), isParallelCapable);
      myModuleName = moduleName;
    }

    @Override
    public String toString() {
      return "DesignTimeClassLoader:" + myModuleName;
    }
  }
}
