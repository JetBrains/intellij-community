/*
 * @author: Eugene Zhuravlev
 * Date: Mar 6, 2003
 * Time: 12:10:11 PM
 */
package com.intellij.ide.plugins.cl;

import com.intellij.ide.plugins.PluginManager;
import sun.misc.CompoundEnumeration;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;

public class PluginClassLoader extends IdeaClassLoader{
  private final ClassLoader[] myParents;
  private final String myPluginName;
  private final File myLibDirectory;

  public PluginClassLoader(List urls, ClassLoader[] parents, String pluginName, File pluginRoot) {
    super(urls, null);
    myParents = parents;
    myPluginName = pluginName;

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
        PluginManager.addPluginClass(c.getName(), myPluginName);
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
      final Method findResourceMethod = cl.getClass().getDeclaredMethod("findResource", new Class[] {String.class});
      findResourceMethod.setAccessible(true);
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
      final Method findResourceMethod = getFindResourceMethod(cl.getClass());
      if (findResourceMethod == null) {
        return null;
      }
      findResourceMethod.setAccessible(true);
      return (Enumeration)findResourceMethod.invoke(cl, new Object[] {resourceName});
    }
    catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  private Method getFindResourceMethod(final Class clClass) {
    try {
      return clClass.getDeclaredMethod("findResources", new Class[] {String.class});
    }
    catch (NoSuchMethodException e) {
      final Class superclass = clClass.getSuperclass();
      if (superclass == null || superclass.equals(Object.class)) {
        return null;
      }
      return getFindResourceMethod(superclass);
    }
  }
}
