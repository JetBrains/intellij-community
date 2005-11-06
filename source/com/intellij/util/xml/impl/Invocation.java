/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.util.xml.impl.DomInvocationHandler;

/**
 * @author peter
 */
public interface Invocation {
  Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable;

}
