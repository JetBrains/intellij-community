/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.uiDesigner.snapShooter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * @author yole
 */
public class SnapShooter {
  private SnapShooter() {
  }

  public static void main(String[] args) throws Throwable {
    int origClassPathSize = Integer.parseInt(args [0]);
    int port = Integer.parseInt(args [1]);

    ClassLoader loader = SnapShooter.class.getClassLoader();
    if (loader instanceof URLClassLoader) {
      URLClassLoader ucl = (URLClassLoader) loader;

      URL[] oldURLs = ucl.getURLs();
      URL[] newURLs = new URL[origClassPathSize];
      final int startIndex = oldURLs.length - origClassPathSize;
      System.arraycopy(oldURLs, startIndex, newURLs, 0, origClassPathSize);
      loader = new URLClassLoader(newURLs, null);
      Thread.currentThread().setContextClassLoader(loader);
    }

    final Thread thread = new Thread(new SnapShooterDaemon(port),"snapshooter");
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
      m.invoke(null, (Object) parms);
    } catch (InvocationTargetException ite) {
      throw ite.getTargetException();
    }
  }

  private static void ensureAccess(Object reflectionObject) {
    // need to call setAccessible here in order to be able to launch package-local classes
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
