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
package com.intellij.patterns;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementVisitor;
import com.intellij.util.xml.reflect.AbstractDomChildrenDescription;
import com.intellij.util.xml.reflect.DomChildrenDescription;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public class DomElementPattern<T extends DomElement,Self extends DomElementPattern<T,Self>> extends TreeElementPattern<DomElement,T,Self> {
  protected DomElementPattern(final Class<T> aClass) {
    super(aClass);
  }

  protected DomElementPattern(@NotNull final InitialPatternCondition<T> condition) {
    super(condition);
  }

  @Override
  protected DomElement getParent(@NotNull DomElement t) {
    return t.getParent();
  }

  @Override
  protected DomElement[] getChildren(@NotNull final DomElement domElement) {
    final List<DomElement> children = new ArrayList<>();
    domElement.acceptChildren(new DomElementVisitor() {
      @Override
      public void visitDomElement(final DomElement element) {
        children.add(element);
      }
    });
    return children.toArray(new DomElement[children.size()]);
  }

  public static class Capture<T extends DomElement> extends DomElementPattern<T, Capture<T>> {
    protected Capture(final Class<T> aClass) {
      super(aClass);
    }

  }

  public Self withChild(@NonNls @NotNull final String localName, final ElementPattern pattern) {
    return with(new PatternCondition<T>("withChild") {
      @Override
      public boolean accepts(@NotNull final T t, final ProcessingContext context) {
        for (final AbstractDomChildrenDescription description : t.getGenericInfo().getChildrenDescriptions()) {
          if (!(description instanceof DomChildrenDescription) || localName.equals(((DomChildrenDescription)description).getXmlElementName())) {
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
