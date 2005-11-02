/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

/**
 * @author peter
 */
public interface Invocation {
  Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable;

}
