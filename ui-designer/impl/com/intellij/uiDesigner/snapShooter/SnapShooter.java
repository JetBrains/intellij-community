/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.snapShooter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author yole
 */
public class SnapShooter {
  private SnapShooter() {
  }

  public static void main(String[] args) throws Throwable {
    System.out.println("Initializing SnapShooter");

    int port = Integer.parseInt(args [0]);

    final Thread thread = new Thread(new SnapShooterDaemon(port));
    thread.setDaemon(true);
    thread.start();

    String mainClass = args[1];
    String[] parms = new String[args.length - 2];
    for (int j = 2; j < args.length; j++) {
      parms[j - 2] = args[j];
    }
    //noinspection HardCodedStringLiteral
    Method m = Class.forName(mainClass).getMethod("main", parms.getClass());
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
      //noinspection HardCodedStringLiteral
      final Method setAccessibleMethod = aClass.getMethod("setAccessible", boolean.class);
      setAccessibleMethod.invoke(reflectionObject, Boolean.TRUE);
    }
    catch (Exception e) {
      // the method not found
    }
  }
}
