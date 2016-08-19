/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.uiDesigner;

import com.intellij.ProjectTopics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.jar.JarFileSystemImpl;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class LoaderFactory {
  private final Project myProject;

  private final ConcurrentMap<Module, ClassLoader> myModule2ClassLoader;
  private ClassLoader myProjectClassLoader = null;
  private final MessageBusConnection myConnection;

  public static LoaderFactory getInstance(final Project project) {
    return ServiceManager.getService(project, LoaderFactory.class);
  }

  public LoaderFactory(final Project project) {
    myProject = project;
    myModule2ClassLoader = ContainerUtil.createConcurrentWeakMap();
    myConnection = myProject.getMessageBus().connect();
    myConnection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      public void rootsChanged(final ModuleRootEvent event) {
        clearClassLoaderCache();
      }
    });

    Disposer.register(project, new Disposable() {
      public void dispose() {
        myConnection.disconnect();
        myModule2ClassLoader.clear();
      }
    });
  }

  @NotNull public ClassLoader getLoader(final VirtualFile formFile) {
    final Module module = ModuleUtil.findModuleForFile(formFile, myProject);
    if (module == null) {
      return getClass().getClassLoader();
    }

    return getLoader(module);
  }

  public ClassLoader getLoader(final Module module) {
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
    final ArrayList<URL> urls = new ArrayList<>();
    final VirtualFileManager manager = VirtualFileManager.getInstance();
    final JarFileSystemImpl fileSystem = (JarFileSystemImpl)JarFileSystem.getInstance();
    final StringTokenizer tokenizer = new StringTokenizer(runClasspath, File.pathSeparator);
    while (tokenizer.hasMoreTokens()) {
      final String s = tokenizer.nextToken();
      try {
        VirtualFile vFile = manager.findFileByUrl(VfsUtil.pathToUrl(s));
        final File realFile = fileSystem.getMirroredFile(vFile);
        urls.add(realFile != null ? realFile.toURI().toURL() : new File(s).toURI().toURL());
      }
      catch (Exception e) {
        // ignore ?
      }
    }

    try {
      urls.add(new File(PathUtil.getJarPathForClass(Spacer.class)).toURI().toURL());
    }
    catch (MalformedURLException ignored) {
      // ignore
    }

    final URL[] _urls = urls.toArray(new URL[urls.size()]);
    return new DesignTimeClassLoader(Arrays.asList(_urls), LoaderFactory.class.getClassLoader(), moduleName);
  }

  public void clearClassLoaderCache() {
    // clear classes with invalid classloader from UIManager cache
    final UIDefaults uiDefaults = UIManager.getDefaults();
    for (Iterator it = uiDefaults.keySet().iterator(); it.hasNext();) {
      Object key = it.next();
      Object value = uiDefaults.get(key);
      if (value instanceof Class) {
        ClassLoader loader = ((Class)value).getClassLoader();
        if (loader instanceof DesignTimeClassLoader) {
          it.remove();
        }
      }
    }
    myModule2ClassLoader.clear();
    myProjectClassLoader = null;
  }

  private static class DesignTimeClassLoader extends UrlClassLoader {
    static { registerAsParallelCapable(); }

    private final String myModuleName;

    public DesignTimeClassLoader(final List<URL> urls, final ClassLoader parent, final String moduleName) {
      super(build().urls(urls).parent(parent));
      myModuleName = moduleName;
    }

    @Override
    public String toString() {
      return "DesignTimeClassLoader:" + myModuleName;
    }
  }
}