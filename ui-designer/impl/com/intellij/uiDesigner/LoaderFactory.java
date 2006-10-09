package com.intellij.uiDesigner;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ProjectRootsTraversing;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.PathUtil;
import com.intellij.util.lang.UrlClassLoader;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class LoaderFactory implements ProjectComponent, JDOMExternalizable{
  private final Project myProject;

  private final WeakHashMap<Module, ClassLoader> myModule2ClassLoader;
  private final ModuleRootListener myRootsListener = new ModuleRootListener() {
    public void beforeRootsChange(final ModuleRootEvent event) {}

    public void rootsChanged(final ModuleRootEvent event) {
      clearClassLoaderCache();
    }
  };
  private ClassLoader myProjectClassLoader = null;

  public static LoaderFactory getInstance(final Project project) {
    return project.getComponent(LoaderFactory.class);
  }
  
  LoaderFactory(final Project project, ProjectRootManager projectRootManager) {
    myProject = project;
    myModule2ClassLoader = new WeakHashMap<Module, ClassLoader>();
    projectRootManager.addModuleRootListener(myRootsListener);
}

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NotNull
  public String getComponentName() {
    return "GUI Designer component loader factory";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    ProjectRootManager.getInstance(myProject).removeModuleRootListener(myRootsListener);
    myModule2ClassLoader.clear();
  }

  public void readExternal(final Element element) {
  }

  public void writeExternal(final Element element) {
  }

  @NotNull public ClassLoader getLoader(final VirtualFile formFile) {
    final Module module = VfsUtil.getModuleForFile(myProject, formFile);
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

    final String runClasspath = ProjectRootsTraversing.collectRoots(module, ProjectRootsTraversing.FULL_CLASSPATH_RECURSIVE).getPathsString();

    final ClassLoader classLoader = createClassLoader(runClasspath);

    myModule2ClassLoader.put(module, classLoader);

    return classLoader;
  }

  @NotNull public ClassLoader getProjectClassLoader() {
    if (myProjectClassLoader == null) {
      final String runClasspath = ProjectRootsTraversing.collectRoots(myProject, ProjectRootsTraversing.FULL_CLASSPATH_RECURSIVE).getPathsString();
      myProjectClassLoader = createClassLoader(runClasspath);
    }
    return myProjectClassLoader;
  }

  private static ClassLoader createClassLoader(final String runClasspath) {
    final ArrayList<URL> urls = new ArrayList<URL>();
    final StringTokenizer tokenizer = new StringTokenizer(runClasspath, File.pathSeparator);
    while (tokenizer.hasMoreTokens()) {
      final String s = tokenizer.nextToken();
      try {
        urls.add(new File(s).toURI().toURL());
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
    return new DesignTimeClassLoader(Arrays.asList(_urls), null);
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
    public DesignTimeClassLoader(final List<URL> urls, final ClassLoader parent) {
      super(urls, parent);
    }
  }
}
