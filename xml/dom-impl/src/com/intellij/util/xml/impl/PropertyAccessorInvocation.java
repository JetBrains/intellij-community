/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.xml.impl;

import com.intellij.util.xml.JavaMethod;
import com.intellij.util.ArrayUtil;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.ArrayList;

/**
 * @author peter
*/
class PropertyAccessorInvocation implements Invocation {
  final int myLastElement;
  private final JavaMethod[] myMethods;

  public PropertyAccessorInvocation(final JavaMethod[] methods) {
    myMethods = methods;
    myLastElement = myMethods.length - 1;
  }

  @Override
  public final Object invoke(final DomInvocationHandler<?, ?> handler, final Object[] args) throws Throwable {
    return invoke(0, handler.getProxy());
  }

  private Object invoke(final int i, final Object object) throws IllegalAccessException, InvocationTargetException {
    final Object o = myMethods[i].invoke(object, ArrayUtil.EMPTY_OBJECT_ARRAY);
    if (i == myLastElement) return o;

    if (o instanceof List) {
      List<Object> result = new ArrayList<>();
      for (Object o1 : (List)o) {
        result.add(invoke(i + 1, o1));
      }
      return result;
    }
    return invoke(i + 1, o);
  }
}
