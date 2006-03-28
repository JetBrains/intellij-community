/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.JavaMethodSignature;
import com.intellij.util.xml.StableElement;
import com.intellij.pom.Navigatable;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * @author peter
 */
public class InvocationCache {
  private static final Map<JavaMethodSignature, Invocation> ourCoreInvocations = new HashMap<JavaMethodSignature, Invocation>();
  private final Map<JavaMethodSignature, Invocation> myInvocations = new HashMap<JavaMethodSignature, Invocation>();

  static {
    addCoreInvocations(DomElement.class);
    addCoreInvocations(Navigatable.class);
    addCoreInvocations(Object.class);
    try {
      ourCoreInvocations.put(JavaMethodSignature.getSignature(GenericAttributeValue.class.getMethod("getXmlAttribute")), new Invocation() {
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
        ourCoreInvocations.put(JavaMethodSignature.getSignature(method), new Invocation() {
          public Object invoke(DomInvocationHandler handler, Object[] args) throws Throwable {
            final Object o = args[0];
            final DomElement proxy = handler.getProxy();
            return proxy == o || o instanceof StableElement && o.equals(((StableElement)o).getWrappedElement());
          }
        });
      }
      else {
        ourCoreInvocations.put(JavaMethodSignature.getSignature(method), new Invocation() {
          public Object invoke(DomInvocationHandler handler, Object[] args) throws Throwable {
            return method.invoke(handler, args);
          }
        });
      }
    }
  }

  public Invocation getInvocation(JavaMethodSignature method) {
    Invocation invocation = ourCoreInvocations.get(method);
    return invocation != null ? invocation : myInvocations.get(method);
  }

  public void putInvocation(JavaMethodSignature method, Invocation invocation) {
    myInvocations.put(method, invocation);
  }

}
