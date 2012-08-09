package com.intellij.util.xml.impl;

import com.intellij.util.xml.Converter;
import com.intellij.util.xml.SubTag;

/**
 * @author peter
 */
public class SetInvocation implements Invocation {
  private final Converter myConverter;

  protected SetInvocation(final Converter converter) {
    myConverter = converter;
  }

  public Object invoke(final DomInvocationHandler<?, ?> handler, final Object[] args) throws Throwable {
    handler.assertValid();
    final SubTag annotation = handler.getAnnotation(SubTag.class);
    final Object arg = args[0];
    if (annotation != null && annotation.indicator() && arg instanceof Boolean) {
      if ((Boolean)arg) {
        handler.ensureTagExists();
      } else {
        handler.undefineInternal();
      }
    } else {
      String value = myConverter.toString(arg, new ConvertContextImpl(handler));
      if (value == null) {
        handler.undefineInternal();
      } else {
        handler.setValue(value);
      }
    }
    return null;
  }

}
