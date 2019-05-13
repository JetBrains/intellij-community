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

  @Override
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
