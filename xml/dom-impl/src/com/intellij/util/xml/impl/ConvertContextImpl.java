// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.impl;

import com.intellij.openapi.module.Module;
import com.intellij.util.xml.AbstractConvertContext;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;

public class ConvertContextImpl extends AbstractConvertContext {
  private final DomInvocationHandler myHandler;

  public ConvertContextImpl(final DomInvocationHandler handler) {
    myHandler = handler;
  }

  @Override
  public @NotNull DomElement getInvocationElement() {
    return myHandler.getProxy();
  }

  @Override
  public Module getModule() {
    final DomElement domElement = getInvocationElement();
    if (domElement.getManager().isMockElement(domElement)) {
      return getInvocationElement().getModule();
    }
    return super.getModule();
  }
}
