/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

/**
 * @author peter
 */
public class GetFixedChildInvocation implements Invocation {
  private MethodSignature myMethodSignature;

  public GetFixedChildInvocation(final MethodSignature method) {
    myMethodSignature = method;
  }

  public Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable {
    handler.checkInitialized();
    return handler.getFixedChild(myMethodSignature).getProxy();
  }
}
