/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.Converter;

import java.lang.reflect.Method;

/**
 * @author peter
 */
public abstract class GetInvocation implements Invocation {
  private final Converter myConverter;
  private final Method myMethod;

  protected GetInvocation(final Converter converter, final Method method) {
    myMethod = method;
    assert converter != null;
    myConverter = converter;
  }

  public Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable {
    assert handler.isValid();
    final XmlTag tag = handler.getXmlTag();
    final boolean tagNotNull = tag != null;
    if (handler.isIndicator()) {
      if (myConverter == Converter.EMPTY_CONVERTER) {
        return tagNotNull ? "" : null;
      }
      else {
        return tagNotNull;
      }
    }

    final String tagValue = tagNotNull ? getValue(tag, handler) : null;
    return myConverter.fromString(tagValue, new ConvertContextImpl(handler));
  }

  protected abstract String getValue(XmlTag tag, DomInvocationHandler handler);
}
