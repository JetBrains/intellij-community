/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import java.lang.reflect.Method;

/**
 * @author peter
 */
public class GetAttributeChildInvocation implements Invocation {
  private Method myMethod;

  public GetAttributeChildInvocation(final Method method) {
    myMethod = method;
  }

  public Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable {
    handler.checkInitialized();
    return handler.getAttributeChild(myMethod).getProxy();
  }
}
