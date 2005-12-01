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
import java.util.Arrays;

/**
 * @author peter
 */
public class InvocationCache {
  private static final Map<String, Invocation> ourCoreInvocations = new HashMap<String, Invocation>();
  private final Map<String, Invocation> myInvocations = new HashMap<String, Invocation>();

  private static String method2String(Method method) {
    return method.getName() + Arrays.asList(method.getParameterTypes());
  }

  static {
    addCoreInvocations(DomElement.class);
    addCoreInvocations(Object.class);
    try {
      ourCoreInvocations.put(method2String(GenericAttributeValue.class.getMethod("getXmlAttribute")), new Invocation() {
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
        ourCoreInvocations.put(method2String(method), new Invocation() {
          public Object invoke(DomInvocationHandler handler, Object[] args) throws Throwable {
            return handler.getProxy() == args[0];
          }
        });
      }
      else {
        ourCoreInvocations.put(method2String(method), new Invocation() {
          public Object invoke(DomInvocationHandler handler, Object[] args) throws Throwable {
            return method.invoke(handler, args);
          }
        });
      }
    }
  }

  public Invocation getInvocation(Method method) {
    final String key = method2String(method);
    Invocation invocation = ourCoreInvocations.get(key);
    return invocation != null ? invocation : myInvocations.get(key);
  }

  public void putInvocation(Method method, Invocation invocation) {
    myInvocations.put(method2String(method), invocation);
  }

}
