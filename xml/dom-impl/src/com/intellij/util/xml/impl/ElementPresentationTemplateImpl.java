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

import com.intellij.ide.presentation.Presentation;
import com.intellij.ide.presentation.PresentationIconProvider;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.Ref;
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

  private final NullableLazyValue<NullableFunction<DomElement, String>> myNamer = new MyLazyValue<String>() {
    @Override
    String getClassName() {
      return myPresentation.namerClass();
    }
  };

  private final NullableLazyValue<PresentationIconProvider> myIconProvider = new NullableLazyValue<PresentationIconProvider>() {
    @Override
    protected PresentationIconProvider compute() {
      Class<? extends PresentationIconProvider> aClass = myPresentation.iconProviderClass();

      try {
        return aClass == PresentationIconProvider.class ? null : aClass.newInstance();
      }
      catch (Exception e) {
        return null;
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
        if (StringUtil.isNotEmpty(myPresentation.typeName())) return myPresentation.typeName();
        return ElementPresentationManager.getTypeNameForObject(element);
      }

      @Override
      public Icon getIcon() {
        PresentationIconProvider iconProvider = myIconProvider.getValue();
        return iconProvider == null ? myIcon.getValue() : iconProvider.getIcon(element, 0);
      }

      @Override
      public String getDocumentation() {
        final Ref<String> result = new Ref<String>();
        element.acceptChildren(new DomElementVisitor() {
          @Override
          public void visitDomElement(DomElement element) {
            if (element instanceof GenericValue && element.getChildDescription().getAnnotation(Documentation.class) != null) {
              result.set(((GenericValue)element).getStringValue());
            }
          }
        });
        return result.isNull() ? super.getDocumentation() : result.get();
      }
    };
  }

  private abstract class MyLazyValue<T> extends NullableLazyValue<NullableFunction<DomElement, T>> {
    @Override
    protected NullableFunction<DomElement, T> compute() {
      String className = getClassName();
      if (StringUtil.isEmpty(className)) return null;
      try {
        //noinspection unchecked
        return (NullableFunction<DomElement, T>)Class.forName(className, true, myClass.getClassLoader()).newInstance();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    abstract String getClassName();
  }
}
