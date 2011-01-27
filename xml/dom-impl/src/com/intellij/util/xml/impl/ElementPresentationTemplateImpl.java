/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.xml.*;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class ElementPresentationTemplateImpl implements ElementPresentationTemplate {

  private final Presentation myPresentation;
  private final Class<?> myClass;

  private final NullableLazyValue<Icon> myIcon = new NullableLazyValue<Icon>() {
    @Override
    protected Icon compute() {
      if (StringUtil.isEmpty(myPresentation.icon())) return null;
      return IconLoader.getIcon(myPresentation.icon(), myClass);
    }
  };

  private final NullableLazyValue<NullableFunction<DomElement, String>> myNamer = new NullableLazyValue<NullableFunction<DomElement, String>>() {
    @Override
    protected NullableFunction<DomElement, String> compute() {
      if (StringUtil.isEmpty(myPresentation.namerClass())) return null;
      try {
        return (NullableFunction<DomElement, String>)Class.forName(myPresentation.namerClass(), true, myClass.getClassLoader()).newInstance();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  };

  public ElementPresentationTemplateImpl(Presentation presentation, Class<?> aClass) {
    myPresentation = presentation;
    myClass = aClass;
  }

  @Override
  public ElementPresentation createPresentation(final DomElement element) {
    return new ElementPresentation() {
      @Override
      public String getElementName() {
        NullableFunction<DomElement, String> namer = myNamer.getValue();
        return namer == null ? ElementPresentationManager.getElementName(element) : namer.fun(element);
      }

      @Override
      public String getTypeName() {
        return ElementPresentationManager.getTypeNameForObject(element);
      }

      @Override
      public Icon getIcon() {
        return myIcon.getValue();
      }
    };
  }
}
