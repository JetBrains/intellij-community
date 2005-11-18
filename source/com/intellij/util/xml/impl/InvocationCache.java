/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.psi.xml.XmlTag;

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
    try {
      ourCoreInvocations.put(GenericAttributeValue.class.getMethod("getXmlAttribute"), new Invocation() {
          public final Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable {
            final XmlTag tag = handler.getXmlTag();
            return tag != null ? tag.getAttribute(handler.getXmlElementName(), null) : null;
          }
        });
    }
    catch (NoSuchMethodException e) {
      throw new AssertionError();
    }
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
