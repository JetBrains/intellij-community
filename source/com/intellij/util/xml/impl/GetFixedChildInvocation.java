/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.util.xml.JavaMethodSignature;

/**
 * @author peter
 */
public class GetFixedChildInvocation implements Invocation {
  private JavaMethodSignature myMethodSignature;

  public GetFixedChildInvocation(final JavaMethodSignature method) {
    myMethodSignature = method;
  }

  public Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable {
    final Pair<String, Integer> info = handler.getGenericInfo().getFixedChildInfo(myMethodSignature);
    handler.checkInitialized(info.getFirst());
    return handler.getFixedChild(info).getProxy();
  }
}
