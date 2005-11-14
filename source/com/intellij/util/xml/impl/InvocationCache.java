/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.util.xml.DomElement;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.HashMap;

/**
 * @author peter
 */
public class InvocationCache {
  private static final Map<Method, Invocation> ourCoreInvocations = new HashMap<Method, Invocation>();
  private final Map<Method, Invocation> myInvocations = new HashMap<Method, Invocation>();

  static {
    addCoreInvocations(DomElement.class);
    addCoreInvocations(DomProxy.class);
    addCoreInvocations(Object.class);
  }

  private static void addCoreInvocations(final Class<?> aClass) {
    for (final Method method : aClass.getDeclaredMethods()) {
      if ("equals".equals(method.getName())) {
        ourCoreInvocations.put(method, new Invocation() {
          public Object invoke(DomInvocationHandler handler, Object[] args) throws Throwable {
            return handler.getProxy() == args[0];
          }
        });
      }
      else {
        ourCoreInvocations.put(method, new Invocation() {
          public Object invoke(DomInvocationHandler handler, Object[] args) throws Throwable {
            return method.invoke(handler, args);
          }
        });
      }
    }
  }

  public Invocation getInvocation(Method method) {
    Invocation invocation = ourCoreInvocations.get(method);
    return invocation != null ? invocation : myInvocations.get(method);
  }

  public void putInvocation(Method method, Invocation invocation) {
    myInvocations.put(method, invocation);
  }

}
