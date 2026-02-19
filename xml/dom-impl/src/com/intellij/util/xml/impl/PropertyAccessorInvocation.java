// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.impl;

import com.intellij.util.ArrayUtilRt;
import com.intellij.util.xml.JavaMethod;

import java.util.ArrayList;
import java.util.List;

final class PropertyAccessorInvocation implements Invocation {
  private final int myLastElement;
  private final JavaMethod[] myMethods;

  PropertyAccessorInvocation(final JavaMethod[] methods) {
    myMethods = methods;
    myLastElement = myMethods.length - 1;
  }

  @Override
  public Object invoke(DomInvocationHandler handler, Object[] args) {
    return invoke(0, handler.getProxy());
  }

  private Object invoke(final int i, final Object object) {
    final Object o = myMethods[i].invoke(object, ArrayUtilRt.EMPTY_OBJECT_ARRAY);
    if (i == myLastElement) return o;

    if (o instanceof List) {
      List<Object> result = new ArrayList<>();
      for (Object o1 : (List<?>)o) {
        result.add(invoke(i + 1, o1));
      }
      return result;
    }
    return invoke(i + 1, o);
  }
}
