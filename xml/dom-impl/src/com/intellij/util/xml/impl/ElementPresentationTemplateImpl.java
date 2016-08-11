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

import com.intellij.ide.TypePresentationServiceImpl;
import com.intellij.ide.presentation.Presentation;
import com.intellij.openapi.util.Ref;
import com.intellij.util.xml.*;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class ElementPresentationTemplateImpl extends TypePresentationServiceImpl.PresentationTemplateImpl implements ElementPresentationTemplate {

  public ElementPresentationTemplateImpl(Presentation presentation, Class<?> aClass) {
    super(presentation, aClass);
  }

  @Override
  public ElementPresentation createPresentation(final DomElement element) {
    return new ElementPresentation() {
      @Override
      public String getElementName() {
        String name = ElementPresentationTemplateImpl.this.getName(element);
        return name == null ? ElementPresentationManager.getElementName(element) : name;
      }

      @Override
      public String getTypeName() {
        String typeName = ElementPresentationTemplateImpl.this.getTypeName();
        return typeName == null ? ElementPresentationManager.getTypeNameForObject(element) : typeName;
      }

      @Override
      public Icon getIcon() {
        return ElementPresentationTemplateImpl.this.getIcon(element, 0);
      }

      @Override
      public String getDocumentation() {
        final Ref<String> result = new Ref<>();
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
}
