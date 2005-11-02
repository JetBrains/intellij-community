/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlTag;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Arrays;

/**
 * @author peter
 */
public interface Invocation {
  Object invoke(final DomInvocationHandler handler, final Method method, final Object[] args) throws Throwable;

  static class GetValueInvocation implements Invocation {
    public Object invoke(final DomInvocationHandler handler, final Method method, final Object[] args) throws Throwable {
      return handler.getValue(handler.getXmlTag(), method);
    }
  }

  static class GetFixedChildInvocation implements Invocation {
    public Object invoke(final DomInvocationHandler handler, final Method method, final Object[] args) throws Throwable {
      handler.checkInitialized();
      return handler.getFixedChild(method);
    }
  }

  static class GetVariableChildrenInvocation implements Invocation {
    private final String myQname;
    private final int myStartIndex;

    public GetVariableChildrenInvocation(final MethodsMap map, final Method method) {
      myQname = map.getVariableChildrenTagQName(method);
      myStartIndex = map.hasFixedChildrenMethod(myQname) ? 1 : 0;
    }

    public Object invoke(final DomInvocationHandler handler, final Method method, final Object[] args) throws Throwable {
      XmlTag tag = handler.getXmlTag();
      if (tag == null) return Collections.emptyList();

      handler.checkInitialized();
      final XmlTag[] subTags = tag.findSubTags(myQname);
      DomElement[] elements = new DomElement[subTags.length - myStartIndex];
      for (int i = myStartIndex; i < subTags.length; i++) {
        final DomElement element = DomManagerImpl.getCachedElement(subTags[i]);
        assert element != null : "Null annotated element for " + tag.getText() + "; " + myQname + "; " + i;
        elements[i - myStartIndex] = element;
      }
      return Arrays.asList(elements);
    }
  }

  static class SetValueInvocation implements Invocation {
    public Object invoke(final DomInvocationHandler handler, final Method method, final Object[] args) throws Throwable {
      XmlTag tag = handler.ensureTagExists();
      final Object oldValue = handler.getTagValue(tag);
      final Object newValue = args[0];
      handler.setTagValue(tag, handler.convertToString(method, newValue, false));
      handler.getManager().fireEvent(new ValueChangeEvent(handler, oldValue, newValue == null ? "" : newValue));
      return null;
    }
  }
}
