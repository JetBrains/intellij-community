package com.intellij.util.xml.impl;

import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public interface Invocation {
  @Nullable
  Object invoke(final DomInvocationHandler<?, ?> handler, final Object[] args) throws Throwable;

}
