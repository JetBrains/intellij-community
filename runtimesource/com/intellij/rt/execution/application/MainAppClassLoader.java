package com.intellij.rt.execution.application;

import java.net.URLClassLoader;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.StringTokenizer;
import java.util.List;
import java.util.ArrayList;
import java.io.File;

/**
 * @author ven
 */
public class MainAppClassLoader  extends URLClassLoader {
  private static final String USER_CLASSPATH = "idea.user.classpath";
  private Class myAppMainClass;

  private static URL[] makeUrls() {
    List classpath = new ArrayList();
    try {
      String userClassPath = System.getProperty(USER_CLASSPATH, "");
      StringTokenizer tokenizer = new StringTokenizer(userClassPath, File.pathSeparator, false);
      while (tokenizer.hasMoreTokens()) {
        String pathItem = tokenizer.nextToken();
        classpath.add(new File(pathItem).toURL());
      }
    } catch (MalformedURLException e) {
      e.printStackTrace();
    }
    return (URL[]) classpath.toArray(new URL[classpath.size()]);
  }

  public MainAppClassLoader(ClassLoader loader) {
    super(makeUrls(), null);
    myAppMainClass = AppMain.class;
  }

  protected synchronized Class loadClass(String name, boolean resolve) throws ClassNotFoundException {
    if (name.equals(myAppMainClass.getName())) {
      return myAppMainClass;
    }
    return super.loadClass(name, resolve);
  }
}
