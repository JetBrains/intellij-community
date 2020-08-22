// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.impl;

import com.intellij.ide.TypePresentationServiceImpl;
import com.intellij.ide.presentation.Presentation;
import com.intellij.openapi.util.Ref;
import com.intellij.util.xml.*;

import javax.swing.*;

public final class ElementPresentationTemplateImpl extends TypePresentationServiceImpl.PresentationTemplateImpl implements ElementPresentationTemplate {
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
              result.set(((GenericValue<?>)element).getStringValue());
            }
          }
        });
        return result.isNull() ? super.getDocumentation() : result.get();
      }
    };
  }
}
