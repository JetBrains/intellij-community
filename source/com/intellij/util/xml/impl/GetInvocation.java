/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.Converter;

/**
 * @author peter
 */
public abstract class GetInvocation implements Invocation {
  private Converter myConverter;

  protected GetInvocation(final Converter converter) {
    assert converter != null;
    myConverter = converter;
  }

  public Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable {
    assert handler.isValid();
    final XmlTag tag = handler.getXmlTag();
    if (tag != null) {
      final String s = getValue(tag);
      if (s != null) {
        return myConverter.fromString(s, new ConvertContextImpl(handler));
      }
    }
    return null;
  }

  protected abstract String getValue(XmlTag tag);
}
