/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

/**
 * @author peter
 */
public class GetAttributeChildInvocation implements Invocation {
  private MethodSignature myMethodSignature;

  public GetAttributeChildInvocation(final MethodSignature method) {
    myMethodSignature = method;
  }

  public Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable {
    handler.checkAttributesInitialized();
    return handler.getAttributeChild(myMethodSignature).getProxy();
  }
}
