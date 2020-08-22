// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.snapShooter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

/**
 * @author yole
 */
public final class SnapShooter {
  private SnapShooter() {
  }

  public static void main(String[] args) throws Throwable {
    int origClassPathStart = Integer.parseInt(args [0]);
    int port = Integer.parseInt(args [1]);

    ClassLoader loader = SnapShooter.class.getClassLoader();
    if (loader instanceof URLClassLoader) {
      URLClassLoader ucl = (URLClassLoader) loader;
      // classpath layout: [internal entry]...[internal entry][user entry]...[user entry][idea_rt.jar/intellij.java.rt]
      URL[] oldURLs = ucl.getURLs();
      URL[] newURLs = Arrays.copyOfRange(oldURLs, origClassPathStart, oldURLs.length - 1);
      loader = new URLClassLoader(newURLs, null);
      Thread.currentThread().setContextClassLoader(loader);
    }

    final Thread thread = new Thread(new SnapShooterDaemon(port), "snapshooter");
    thread.setDaemon(true);
    thread.start();

    String mainClass = args[2];
    String[] parms = new String[args.length - 3];
    for (int j = 3; j < args.length; j++) {
      parms[j - 3] = args[j];
    }
    Method m = loader.loadClass(mainClass).getMethod("main", parms.getClass());
    try {
      ensureAccess(m);
      m.invoke(null, (Object)parms);
    }
    catch (InvocationTargetException ite) {
      throw ite.getTargetException();
    }
  }

  private static void ensureAccess(Object reflectionObject) {
    // need to call setAccessible here in order to be able to launch package-private classes
    // calling setAccessible() via reflection because the method is missing from java version 1.1.x
    final Class aClass = reflectionObject.getClass();
    try {
      final Method setAccessibleMethod = aClass.getMethod("setAccessible", boolean.class);
      setAccessibleMethod.invoke(reflectionObject, Boolean.TRUE);
    }
    catch (Exception e) {
      // the method not found
    }
  }
}
