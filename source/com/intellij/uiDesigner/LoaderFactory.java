package com.intellij.uiDesigner;

import com.intellij.ide.plugins.cl.IdeaClassLoader;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.PathUtil;
import org.jdom.Element;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.StringTokenizer;
import java.util.WeakHashMap;

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
      myModule2ClassLoader.clear();
    }
  };

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


  /**
   * @return never null
   */ 
  public ClassLoader getLoader(final VirtualFile formFile) {
    final Module module = ModuleUtil.getModuleForFile(myProject, formFile);
    if (module == null) {
      return getClass().getClassLoader();
    }
    
    final ClassLoader cachedLoader = myModule2ClassLoader.get(module);
    if (cachedLoader != null) {
      return cachedLoader;
    }

    final String runClasspath = ProjectRootsTraversing.collectRoots(module, ProjectRootsTraversing.FULL_CLASSPATH_RECURSIVE).getPathsString();

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
    }

    final URL[] _urls = urls.toArray(new URL[urls.size()]);
    //final URLClassLoader classLoader = new URLClassLoader(_urls, null);
    final ClassLoader classLoader = new IdeaClassLoader(Arrays.asList(_urls), null);

    myModule2ClassLoader.put(module, classLoader);

    return classLoader;      
  }
}
