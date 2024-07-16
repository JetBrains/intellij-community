// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns;

import com.intellij.util.ProcessingContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementVisitor;
import com.intellij.util.xml.reflect.AbstractDomChildrenDescription;
import com.intellij.util.xml.reflect.DomChildrenDescription;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DomElementPattern<T extends DomElement,Self extends DomElementPattern<T,Self>> extends TreeElementPattern<DomElement,T,Self> {
  protected DomElementPattern(final Class<T> aClass) {
    super(aClass);
  }

  protected DomElementPattern(final @NotNull InitialPatternCondition<T> condition) {
    super(condition);
  }

  @Override
  protected DomElement getParent(@NotNull DomElement t) {
    return t.getParent();
  }

  @Override
  protected DomElement[] getChildren(final @NotNull DomElement domElement) {
    final List<DomElement> children = new ArrayList<>();
    domElement.acceptChildren(new DomElementVisitor() {
      @Override
      public void visitDomElement(final DomElement element) {
        children.add(element);
      }
    });
    return children.toArray(DomElement.EMPTY_ARRAY);
  }

  public static class Capture<T extends DomElement> extends DomElementPattern<T, Capture<T>> {
    protected Capture(final Class<T> aClass) {
      super(aClass);
    }

  }

  public Self withChild(final @NonNls @NotNull String localName, final ElementPattern pattern) {
    return with(new PatternCondition<>("withChild") {
      @Override
      public boolean accepts(final @NotNull T t, final ProcessingContext context) {
        for (final AbstractDomChildrenDescription description : t.getGenericInfo().getChildrenDescriptions()) {
          if (!(description instanceof DomChildrenDescription) ||
              localName.equals(((DomChildrenDescription)description).getXmlElementName())) {
            for (final DomElement element : description.getValues(t)) {
              if (localName.equals(element.getXmlElementName()) && pattern.accepts(element, context)) {
                return true;
              }
            }
          }
        }
        return false;
      }
    });
  }


}
