/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;

/**
 * @author peter
 */
public abstract class SetInvocation implements Invocation {
  private Converter myConverter;

  protected SetInvocation(final Converter converter) {
    myConverter = converter;
  }

  public Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable {
    XmlTag tag = handler.ensureTagExists();
    handler.getManager().setChanging(true);
    try {
      final String oldValue = getValue(tag);
      if (args[0] == null) {
        clearValue(tag);
        handler.getManager().fireEvent(createEvent(handler, oldValue, null));
      } else {
        final String newValue = myConverter.toString(args[0], new ConvertContext(handler));
        setValue(tag, newValue);
        handler.getManager().fireEvent(createEvent(handler, oldValue, newValue));
      }
    }
    finally {
      handler.getManager().setChanging(false);
    }
    return null;
  }

  protected abstract String getValue(XmlTag tag);

  protected abstract DomChangeEvent createEvent(DomInvocationHandler handler, String oldValue, String newValue);

  protected abstract void setValue(XmlTag tag, String value) throws IncorrectOperationException;

  protected abstract void clearValue(XmlTag tag) throws IncorrectOperationException;
}
