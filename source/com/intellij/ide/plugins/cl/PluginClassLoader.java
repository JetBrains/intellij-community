/*
 * @author: Eugene Zhuravlev
 * Date: Mar 6, 2003
 * Time: 12:10:11 PM
 */
package com.intellij.ide.plugins.cl;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginId;
import sun.misc.CompoundEnumeration;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;

public class PluginClassLoader extends IdeaClassLoader{
  private final ClassLoader[] myParents;
  private final PluginId myPluginId;
  private final File myLibDirectory;

  public PluginClassLoader(List urls, ClassLoader[] parents, PluginId pluginId, File pluginRoot) {
    super(urls, null);
    myParents = parents;
    myPluginId = pluginId;

    final File file = new File(pluginRoot, "lib");
    myLibDirectory = file.exists()? file : null;
  }

  // changed sequence in which classes are searched, this is essential if plugin uses library, a different version of which
  // is used in IDEA.
  protected synchronized Class loadClass(String name, boolean resolve)  throws ClassNotFoundException {
    Class c = findLoadedClass(name);
    if (c == null) {
      try {
        c = findClass(name);
        PluginManager.addPluginClass(c.getName(), myPluginId);
      }
      catch (ClassNotFoundException e) {
        for (int idx = 0; idx < myParents.length; idx++) {
          try {
            c = myParents[idx].loadClass(name);
            break;
          }
          catch (ClassNotFoundException ignoreAndContinue) {
          }
        }
        if (c == null) {
          throw new ClassNotFoundException(name);
        }
      }
    }
    if (resolve) {
      resolveClass(c);
    }


    return c;
  }

  public URL findResource(final String name) {
    final URL resource = super.findResource(name);
    if (resource != null) {
      return resource;
    }
    for (int idx = 0; idx < myParents.length; idx++) {
      final URL parentResource = fetchResource(myParents[idx], name);
      if (parentResource != null) {
        return parentResource;
      }
    }
    return null;
  }

  public Enumeration findResources(final String name) throws IOException {
    final Enumeration[] resources = new Enumeration[myParents.length + 1];
    resources[0] = super.findResources(name);
    for (int idx = 0; idx < myParents.length; idx++) {
      resources[idx + 1] = fetchResources(myParents[idx], name);
    }
    return new CompoundEnumeration(resources);
  }

  protected String findLibrary(String libName) {
    if (myLibDirectory == null) {
      return null;
    }
    final File libraryFile = new File(myLibDirectory, System.mapLibraryName(libName));
    return libraryFile.exists()? libraryFile.getAbsolutePath() : null;
  }


  private URL fetchResource(ClassLoader cl, String resourceName) {
    //protected URL findResource(String s)
    try {
      final Method findResourceMethod = getFindResourceMethod(cl.getClass(), "findResource");
      return (URL)findResourceMethod.invoke(cl, new Object[] {resourceName});
    }
    catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  private Enumeration fetchResources(ClassLoader cl, String resourceName) {
    //protected Enumeration findResources(String s) throws IOException
    try {
      final Method findResourceMethod = getFindResourceMethod(cl.getClass(), "findResources");
      if (findResourceMethod == null) {
        return null;
      }
      return (Enumeration)findResourceMethod.invoke(cl, new Object[] {resourceName});
    }
    catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  private Method getFindResourceMethod(final Class clClass, final String methodName) {
    try {
      final Method declaredMethod = clClass.getDeclaredMethod(methodName, new Class[]{String.class});
      declaredMethod.setAccessible(true);
      return declaredMethod;
    }
    catch (NoSuchMethodException e) {
      final Class superclass = clClass.getSuperclass();
      if (superclass == null || superclass.equals(Object.class)) {
        return null;
      }
      return getFindResourceMethod(superclass, methodName);
    }
  }
}
